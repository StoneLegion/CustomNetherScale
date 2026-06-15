package ru.windcorp.piwcs.cns;

import java.util.function.BiFunction;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.minecraft.launchwrapper.IClassTransformer;
import openmods.Log;
import openmods.asm.VisitorHelper;
import openmods.asm.VisitorHelper.TransformProvider;

public class CNSTransformer implements IClassTransformer {
	
	/*
	 * TARGET constants represent the method to patch
	 */
	public static final String TARGET_CLASS       = "net.minecraft.world.WorldProvider";
	public static final String TARGET_METHOD_MCP  = "getMovementFactor";
	public static final String TARGET_METHOD_SRG  = "getMovementFactor"; // Forge method
	public static final String TARGET_METHOD_DESC = "()D";

	public static final String TRANSFER_TARGET_CLASS       = "net.minecraft.server.management.ServerConfigurationManager";
	public static final String TRANSFER_TARGET_METHOD_MCP  = "transferEntityToWorld";
	public static final String TRANSFER_TARGET_METHOD_SRG  = "func_82448_a";
	public static final String TRANSFER_TARGET_METHOD_DESC = "(Lnet/minecraft/entity/Entity;ILnet/minecraft/world/WorldServer;Lnet/minecraft/world/WorldServer;Lnet/minecraft/world/Teleporter;)V";
	
	/*
	 * DESTINATION constants represent the method redirect to
	 */
	public static final String DESTINATION_CLASS  = CNSHooks.class.getName();
	public static final String DESTINATION_METHOD = "getScale";
	public static final String DESTINATION_DESC   = "(" + toFieldType(TARGET_CLASS) + ")D";

	@Override
	public byte[] transform(String name, String transformedName, byte[] basicClass) {
		
		if (transformedName.equals(TARGET_CLASS)) {
			return VisitorHelper.apply(basicClass, name, createTransformProvider(
					ClassWriter.COMPUTE_FRAMES,
					CNSClassVisitor::new
			));
		}
		
		if (transformedName.equals(TRANSFER_TARGET_CLASS)) {
			return VisitorHelper.apply(basicClass, name, createTransformProvider(
					ClassWriter.COMPUTE_FRAMES,
					CNSTransferClassVisitor::new
			));
		}

		return basicClass;
		
	}
	
	private static TransformProvider createTransformProvider(
			int mode,
			BiFunction<String, ClassVisitor, ClassVisitor> provider
	) {
		return new TransformProvider(mode) {
			@Override
			public ClassVisitor createVisitor(String obfuscatedName, ClassVisitor cv) {
				return provider.apply(obfuscatedName, cv);
			}
		};
	}
	
	/**
	 * Converts the given Fully Qualified Name of a class into a binary class name,
	 * as described in
	 * <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.2.1">
	 * Section 4.2.1 of Java Virtual Machine Specification</a>.
	 * <p>
	 * <i>Example</i>:<br />
	 * {@code toBinaryName("java.lang.Thread")} = {@code "java/lang/Thread"}
	 * @param fqn Fully qualified name of a class
	 * @return the corresponding binary name
	 */
	private static String toBinaryName(String fqn) {
		return fqn.replace('.', '/');
	}
	
	/**
	 * Converts the given Fully Qualified Name of a class into a FieldType,
	 * as described in
	 * <a href="https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.2">
	 * Section 4.3.2 of Java Virtual Machine Specification</a>.
	 * <p>
	 * <i>Example</i>:<br />
	 * {@code toBinaryName("java.lang.Thread")} = {@code "Ljava/lang/Thread;"}
	 * @param fqn Fully qualified name of a class
	 * @return the corresponding FieldType name
	 */
	private static String toFieldType(String fqn) {
		return "L" + toBinaryName(fqn) + ";";
	}
	
	private static class CNSClassVisitor extends ClassVisitor {
		
		private final AdvancedMethodMatcher targetMethodMatcher;
		
		private boolean patchApplied = false;

		public CNSClassVisitor(String obfuscatedName, ClassVisitor cv) {
			super(Opcodes.ASM5, cv);
			
			this.targetMethodMatcher = new AdvancedMethodMatcher(
					obfuscatedName,
					TARGET_METHOD_DESC,
					TARGET_METHOD_MCP,
					TARGET_METHOD_SRG
			);
		}
		
		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			
			MethodVisitor superVisitor = super.visitMethod(access, name, desc, signature, exceptions);
			
			if (targetMethodMatcher.match(name, desc)) {
				patchApplied = true;
				return new CNSMethodVisitor(superVisitor);
			}
			
			return superVisitor;
		}
		
		@Override
		public void visitEnd() {
			super.visitEnd();
			
			if (patchApplied) {
				Log.debug("[Custom Nether Scale] Patch applied");
			} else {
				Log.severe("[Custom Nether Scale] Patch not applied, mod will NOT work");
			}
		}
	}

	private static class CNSTransferClassVisitor extends ClassVisitor {

		private final AdvancedMethodMatcher targetMethodMatcher;

		private boolean patchApplied = false;

		public CNSTransferClassVisitor(String obfuscatedName, ClassVisitor cv) {
			super(Opcodes.ASM5, cv);

			this.targetMethodMatcher = new AdvancedMethodMatcher(
					obfuscatedName,
					TRANSFER_TARGET_METHOD_DESC,
					TRANSFER_TARGET_METHOD_MCP,
					TRANSFER_TARGET_METHOD_SRG
			);
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {

			MethodVisitor superVisitor = super.visitMethod(access, name, desc, signature, exceptions);

			if (targetMethodMatcher.match(name, desc)) {
				patchApplied = true;
				return new CNSTransferMethodVisitor(superVisitor);
			}

			return superVisitor;
		}

		@Override
		public void visitEnd() {
			super.visitEnd();

			if (patchApplied) {
				Log.debug("[Custom Nether Scale] Transfer patch applied");
			} else {
				Log.severe("[Custom Nether Scale] Transfer patch not applied, mod may not work on GTNH 2.9+");
			}
		}
	}
		
	private static class CNSMethodVisitor extends MethodVisitor {

		public CNSMethodVisitor(MethodVisitor mv) {
			super(Opcodes.ASM5, mv);
		}
		
		@Override
		public void visitCode() {
			super.visitVarInsn(Opcodes.ALOAD, 0); // Load 'this'
			super.visitMethodInsn(
					Opcodes.INVOKESTATIC,
					toBinaryName(DESTINATION_CLASS),
					DESTINATION_METHOD,
					DESTINATION_DESC,
					false
			); // Invoke destination method
			super.visitInsn(Opcodes.DRETURN); // Return the value that destination method returned
		}
		
	}

	private static class CNSTransferMethodVisitor extends MethodVisitor {

		private boolean patchedRatio = false;

		public CNSTransferMethodVisitor(MethodVisitor mv) {
			super(Opcodes.ASM5, mv);
		}

		@Override
		public void visitVarInsn(int opcode, int var) {
			super.visitVarInsn(opcode, var);

			if (!patchedRatio && opcode == Opcodes.DSTORE && var == 8) {
				super.visitVarInsn(Opcodes.ALOAD, 3);
				super.visitVarInsn(Opcodes.ALOAD, 4);
				super.visitMethodInsn(
						Opcodes.INVOKESTATIC,
						toBinaryName(DESTINATION_CLASS),
						"getTransferRatio",
						"(Lnet/minecraft/world/WorldServer;Lnet/minecraft/world/WorldServer;)D",
						false
				);
				super.visitVarInsn(Opcodes.DSTORE, 8);
				patchedRatio = true;
			}
		}
	}

}
