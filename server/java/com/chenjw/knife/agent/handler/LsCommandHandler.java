package com.chenjw.knife.agent.handler;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.chenjw.knife.agent.Agent;
import com.chenjw.knife.agent.args.ArgDef;
import com.chenjw.knife.agent.args.Args;
import com.chenjw.knife.agent.bytecode.javassist.Helper;
import com.chenjw.knife.agent.constants.Constants;
import com.chenjw.knife.agent.core.CommandDispatcher;
import com.chenjw.knife.agent.core.CommandHandler;
import com.chenjw.knife.agent.manager.ContextManager;
import com.chenjw.knife.agent.manager.ObjectRecordManager;
import com.chenjw.knife.agent.utils.NativeHelper;
import com.chenjw.knife.agent.utils.ReflectHelper;
import com.chenjw.knife.agent.utils.ResultHelper;
import com.chenjw.knife.agent.utils.ToStringHelper;
import com.chenjw.knife.core.model.ArrayElementInfo;
import com.chenjw.knife.core.model.ClassConstructorInfo;
import com.chenjw.knife.core.model.ClassFieldInfo;
import com.chenjw.knife.core.model.ClassMethodInfo;
import com.chenjw.knife.core.model.ConstructorInfo;
import com.chenjw.knife.core.model.FieldInfo;
import com.chenjw.knife.core.model.MethodInfo;
import com.chenjw.knife.core.model.ObjectInfo;
import com.chenjw.knife.core.result.Result;
import com.chenjw.knife.utils.StringHelper;

public class LsCommandHandler implements CommandHandler {

	public class ClassOrObject {
		private Object obj = null;
		private Class<?> clazz = null;

		public boolean isObject() {
			return obj != null;
		}

		public boolean isClazz() {
			return obj == null && clazz != null;
		}

		public boolean isNotFound() {
			return obj == null && clazz == null;
		}

		public Object getObj() {
			return obj;
		}

		public void setObj(Object obj) {
			if (obj != null) {
				this.obj = obj;
				this.clazz = obj.getClass();
			}
		}

		public Class<?> getClazz() {
			return clazz;
		}

		public void setClazz(Class<?> clazz) {
			this.clazz = clazz;
		}
	}

	private ClassOrObject getTarget(Args args) {
		ClassOrObject target = new ClassOrObject();
		String className = args.arg("classname");
		if (StringHelper.isBlank(className)) {
			target.setObj(ContextManager.getInstance().get(Constants.THIS));

		} else if (StringHelper.isNumeric(className)) {
			target.setObj(ObjectRecordManager.getInstance().get(
					Integer.parseInt(className)));

		} else {
			target.setClazz(Helper.findClass(className));
		}
		return target;
	}

	private void lsField(Args args) {
		ClassOrObject target = getTarget(args);
		if (target.isNotFound()) {
			Agent.sendResult(ResultHelper.newErrorResult("not found!"));
			return;
		}
		List<FieldInfo> fieldInfos = new ArrayList<FieldInfo>();
		if (target.getClazz() != null) {
			Map<Field, Object> fieldMap = NativeHelper
					.getStaticFieldValues(target.getClazz());
			for (Entry<Field, Object> entry : fieldMap.entrySet()) {
				FieldInfo fieldInfo = new FieldInfo();
				fieldInfo.setStatic(true);
				fieldInfo.setName(entry.getKey().getName());
				fieldInfo.setObjectId(ObjectRecordManager.getInstance().toId(
						entry.getValue()));
				fieldInfo.setValueString(toString(args, entry.getValue()));
			}
		}
		if (target.getObj() != null) {
			Map<Field, Object> fieldMap = NativeHelper.getFieldValues(target
					.getObj());
			for (Entry<Field, Object> entry : fieldMap.entrySet()) {
				FieldInfo fieldInfo = new FieldInfo();
				fieldInfo.setStatic(false);
				fieldInfo.setName(entry.getKey().getName());
				fieldInfo.setObjectId(ObjectRecordManager.getInstance().toId(
						entry.getValue()));
				fieldInfo.setValueString(toString(args, entry.getValue()));
			}
		}
		Result<ClassFieldInfo> result = new Result<ClassFieldInfo>();
		ClassFieldInfo classFieldInfo = new ClassFieldInfo();
		classFieldInfo.setFields(fieldInfos.toArray(new FieldInfo[fieldInfos
				.size()]));
		result.setSuccess(true);
		result.setContent(classFieldInfo);
		Agent.sendResult(result);
	}

	private void lsMethod(Args args) {
		ClassOrObject target = getTarget(args);
		if (target.isNotFound()) {
			Agent.sendResult(ResultHelper.newErrorResult("not found!"));
			return;
		}
		List<MethodInfo> methodInfos = new ArrayList<MethodInfo>();
		List<Method> list = new ArrayList<Method>();

		if (target.getClazz() != null) {
			for (Method method : ReflectHelper.getMethods(target.getClazz())) {
				if (Modifier.isStatic(method.getModifiers())) {
					MethodInfo methodInfo = new MethodInfo();
					methodInfo.setStatic(true);
					methodInfo.setName(method.getName());
					methodInfo.setParamClassNames(getParamClassNames(method
							.getParameterTypes()));
					methodInfos.add(methodInfo);
					list.add(method);

				}
			}
		}
		if (target.getObj() != null) {
			for (Method method : ReflectHelper.getMethods(target.getClazz())) {
				if (!Modifier.isStatic(method.getModifiers())) {
					MethodInfo methodInfo = new MethodInfo();
					methodInfo.setStatic(false);
					methodInfo.setName(method.getName());
					methodInfo.setParamClassNames(getParamClassNames(method
							.getParameterTypes()));
					methodInfos.add(methodInfo);
					list.add(method);

				}
			}
		}
		ContextManager.getInstance().put(Constants.METHOD_LIST,
				list.toArray(new Method[list.size()]));
		ClassMethodInfo classMethodInfo = new ClassMethodInfo();
		classMethodInfo.setMethods(methodInfos
				.toArray(new MethodInfo[methodInfos.size()]));
		Result<ClassMethodInfo> result = new Result<ClassMethodInfo>();
		result.setSuccess(true);
		result.setContent(classMethodInfo);
		Agent.sendResult(result);
	}

	private void lsConstruct(Args args) {
		ClassOrObject target = getTarget(args);
		if (target.isNotFound()) {
			Agent.sendResult(ResultHelper.newErrorResult("not found!"));
			return;
		}
		List<ConstructorInfo> constructorInfos = new ArrayList<ConstructorInfo>();
		List<Constructor<?>> list = new ArrayList<Constructor<?>>();
		if (target.getClass() != null) {
			Constructor<?>[] constructors = target.getClass()
					.getDeclaredConstructors();
			for (Constructor<?> constructor : constructors) {
				ConstructorInfo constructorInfo = new ConstructorInfo();
				constructorInfo
						.setParamClassNames(getParamClassNames(constructor
								.getParameterTypes()));
				constructorInfos.add(constructorInfo);
				list.add(constructor);
			}
		}
		ContextManager.getInstance().put(Constants.CONSTRUCTOR_LIST,
				list.toArray(new Constructor[list.size()]));
		ClassConstructorInfo classConstructorInfo = new ClassConstructorInfo();
		classConstructorInfo.setConstructors(constructorInfos
				.toArray(new ConstructorInfo[constructorInfos.size()]));
		Result<ClassConstructorInfo> result = new Result<ClassConstructorInfo>();
		result.setSuccess(true);
		result.setContent(classConstructorInfo);
		Agent.sendResult(result);

	}

	private void lsClass(Args args) {
		ClassOrObject target = getTarget(args);
		if (target.isNotFound()) {
			Agent.sendResult(ResultHelper.newErrorResult("not found!"));
			return;
		}
		Result<ObjectInfo> result = new Result<ObjectInfo>();
		ObjectInfo objectInfo = new ObjectInfo();
		if ((target.getObj() instanceof Throwable)) {
			objectInfo.setThrowable(true);
			objectInfo.setObjectId(ObjectRecordManager.getInstance().toId(
					target.getObj()));
			objectInfo.setValueString(ToStringHelper
					.toExceptionTraceString((Throwable) target.getObj()));
		} else {
			objectInfo.setThrowable(false);
			objectInfo.setObjectId(ObjectRecordManager.getInstance().toId(
					target.getObj()));
			objectInfo.setValueString(toString(args, target.getObj()));
		}
		result.setContent(objectInfo);
		result.setSuccess(true);
		Agent.sendResult(result);
	}

	@SuppressWarnings("unchecked")
	private void lsArray(Args args) {
		ClassOrObject target = getTarget(args);
		if (target.isNotFound()) {
			Agent.sendResult(ResultHelper.newErrorResult("not found!"));
			return;
		}
		List<ArrayElementInfo> arrayElementInfos = new ArrayList<ArrayElementInfo>();
		if (target.getClass().isArray()) {

			for (int i = 0; i < Array.getLength(target.getObj()); i++) {
				Object aObj = Array.get(target.getObj(), i);
				ArrayElementInfo arrayElementInfo = new ArrayElementInfo();
				arrayElementInfo.setObjectId(ObjectRecordManager.getInstance()
						.toId(aObj));
				arrayElementInfo.setValueString(toString(args, aObj));

				arrayElementInfos.add(arrayElementInfo);
			}

		} else if (target.getObj() instanceof List) {
			for (Object aObj : (List<Object>) target.getObj()) {
				ArrayElementInfo arrayElementInfo = new ArrayElementInfo();
				arrayElementInfo.setObjectId(ObjectRecordManager.getInstance()
						.toId(aObj));
				arrayElementInfo.setValueString(toString(args, aObj));

				arrayElementInfos.add(arrayElementInfo);
			}

		} else {
			Result<ClassConstructorInfo> result = new Result<ClassConstructorInfo>();
			result.setErrorMessage("not array!");
			result.setSuccess(false);
			Agent.sendResult(result);
		}

	}

	private static String toString(Args args, Object obj) {
		if (obj == null) {
			return "null";
		}
		String rr = null;
		if (args.option("-d") != null) {
			rr = ToStringHelper.toDetailString(obj);
		} else {
			rr = ToStringHelper.toString(obj);
		}
		return rr;
	}

	public void handle(Args args, CommandDispatcher dispatcher) {
		if (args.option("-f") != null) {
			lsField(args);
		} else if (args.option("-m") != null) {
			lsMethod(args);
		} else if (args.option("-a") != null) {
			lsArray(args);
		} else if (args.option("-c") != null) {
			lsConstruct(args);
		} else {
			lsClass(args);
		}
	}

	public static String[] getParamClassNames(Class<?>[] classes) {
		String[] classNames = new String[classes.length];
		for (int i = 0; i < classes.length; i++) {
			classNames[i] = Helper.makeClassName(classes[i]);
		}
		return classNames;
	}

	@Override
	public void declareArgs(ArgDef argDef) {
		argDef.setCommandName("ls");
		argDef.setDef("[-f] [-m] [-c] [-a] [-d] [<classname>]");
		argDef.setDesc("list fields and methods of the target object.");
		argDef.addOptionDesc(
				"classname",
				"set <classname> to find static fields or methods , if <classname> not set , will apply to target object.");

		argDef.addOptionDesc("-f", "list fields.");
		argDef.addOptionDesc("-m", "list methods.");
		argDef.addOptionDesc("-c", "list constructs.");
		argDef.addOptionDesc("-a", "list array component.");
		argDef.addOptionDesc("-d", "to detail string.");

	}

}
