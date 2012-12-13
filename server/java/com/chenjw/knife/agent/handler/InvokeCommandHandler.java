package com.chenjw.knife.agent.handler;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chenjw.knife.agent.Agent;
import com.chenjw.knife.agent.Profiler;
import com.chenjw.knife.agent.args.ArgDef;
import com.chenjw.knife.agent.args.Args;
import com.chenjw.knife.agent.bytecode.javassist.Helper;
import com.chenjw.knife.agent.constants.Constants;
import com.chenjw.knife.agent.core.CommandDispatcher;
import com.chenjw.knife.agent.core.CommandHandler;
import com.chenjw.knife.agent.filter.Depth0Filter;
import com.chenjw.knife.agent.filter.DepthFilter;
import com.chenjw.knife.agent.filter.ExceptionFilter;
import com.chenjw.knife.agent.filter.Filter;
import com.chenjw.knife.agent.filter.FilterInvocationListener;
import com.chenjw.knife.agent.filter.FixThreadFilter;
import com.chenjw.knife.agent.filter.InstrumentClassLoaderFilter;
import com.chenjw.knife.agent.filter.InstrumentFilter;
import com.chenjw.knife.agent.filter.InvokeFinishFilter;
import com.chenjw.knife.agent.filter.InvokePrintFilter;
import com.chenjw.knife.agent.filter.PatternMethodFilter;
import com.chenjw.knife.agent.filter.ProxyMethodFilter;
import com.chenjw.knife.agent.filter.SystemOperationFilter;
import com.chenjw.knife.agent.filter.TimingFilter;
import com.chenjw.knife.agent.filter.TimingStopFilter;
import com.chenjw.knife.agent.service.ContextService;
import com.chenjw.knife.agent.utils.ClassLoaderHelper;
import com.chenjw.knife.agent.utils.NativeHelper;
import com.chenjw.knife.agent.utils.ParseHelper;
import com.chenjw.knife.agent.utils.ResultHelper;
import com.chenjw.knife.utils.ReflectHelper;
import com.chenjw.knife.utils.StringHelper;
import com.chenjw.knife.utils.invoke.InvokeResult;
import com.chenjw.knife.utils.invoke.MethodInvokeException;

public class InvokeCommandHandler implements CommandHandler {

	public void handle(Args args, CommandDispatcher dispatcher)
			throws Exception {
		boolean isTrace = args.option("-t") != null;
		configStrategy(args);
		invokeMethod(isTrace, args.arg("invoke-expression"));
	}

	private void configStrategy(Args args) throws Exception {
		List<Filter> filters = new ArrayList<Filter>();
		filters.add(new SystemOperationFilter());
		filters.add(new FixThreadFilter(Thread.currentThread()));
		filters.add(new ExceptionFilter());
		filters.add(new TimingStopFilter());
		Map<String, String> tOptions = args.option("-t");
		if (tOptions != null) {
			filters.add(new InstrumentClassLoaderFilter());
			filters.add(new InstrumentFilter());
		}
		Map<String, String> fOptions = args.option("-f");
		if (fOptions != null) {
			filters.add(new PatternMethodFilter(fOptions
					.get("filter-expression")));
		}
		filters.add(new ProxyMethodFilter());
		filters.add(new DepthFilter());
		if (tOptions == null) {
			filters.add(new Depth0Filter());
		}
		filters.add(new TimingFilter());
		filters.add(new InvokeFinishFilter());
		filters.add(new InvokePrintFilter());
		Profiler.listener = new FilterInvocationListener(filters);
	}

	private void invokeMethod(boolean isTrace, String methodSig)
			throws Exception {

		String argStr = methodSig;
		String m = StringHelper.substringBefore(argStr, "(");
		m = m.trim();
		Method method = null;
		if (StringHelper.isNumeric(m)) {
			method = ((Method[]) ContextService.getInstance().get(
					Constants.METHOD_LIST))[Integer.parseInt(m)];
		} else {
			if (m.indexOf(".") != -1) {
				String className = StringHelper.substringBeforeLast(m, ".");
				m = StringHelper.substringAfterLast(m, ".");
				Class<?> clazz = NativeHelper.findLoadedClass(className);
				if (clazz == null) {
					clazz = Helper.findClass(className);
				}
				if (clazz == null) {
					Agent.sendResult(ResultHelper.newErrorResult("class "
							+ className + " not found!"));
					return;
				}
				Method[] methods = ReflectHelper.getMethods(clazz);
				for (Method tm : methods) {
					if (tm.getName().equals(m)) {
						if (Modifier.isStatic(tm.getModifiers())) {
							method = tm;
							break;
						}
					}
				}

			} else {
				Object obj = ContextService.getInstance().get(Constants.THIS);
				if (obj == null) {
					Agent.sendResult(ResultHelper.newErrorResult("not found!"));
					return;
				}
				Method[] methods = ReflectHelper.getMethods(obj.getClass());
				for (Method tm : methods) {
					if (tm.getName().equals(m)) {
						method = tm;
						break;
					}
				}
			}
		}
		if (method == null) {
			Agent.sendResult(ResultHelper.newErrorResult("method not found!"));
			return;
		}
		Object[] mArgs = ParseHelper.parseMethodArgs(
				StringHelper.substringBeforeLast(
						StringHelper.substringAfter(argStr, "("), ")"),
				method.getParameterTypes());

		if (Modifier.isStatic(method.getModifiers())) {
			invoke(isTrace, method, null, mArgs);
		} else {
			invoke(isTrace, method,
					ContextService.getInstance().get(Constants.THIS), mArgs);
		}
	}

	private void invoke(boolean isTrace, Method method, Object thisObject,
			Object[] args) throws Exception {
		// 重置classloader
		ClassLoaderHelper.resetClassLoader(method.getDeclaringClass());
		boolean isStatic = Modifier.isStatic(method.getModifiers());
		Class<?> clazz = null;
		if (isStatic) {
			thisObject = null;
			clazz = method.getDeclaringClass();
		} else {
			clazz = thisObject.getClass();
		}
		try {
			Profiler.start(thisObject, clazz.getName(), method.getName(), args,
					null, -1);
			if (isTrace) {
				Profiler.profile(method);
			}
			InvokeResult r = null;
			if (isStatic) {
				r = ReflectHelper.invokeStaticMethod(method, args);
			} else {
				r = ReflectHelper.invokeMethod(thisObject, method, args);
			}

			if (r.isSuccess()) {
				Profiler.returnEnd(thisObject, clazz.getName(),
						method.getName(), args, r.getResult());
			} else {
				Profiler.exceptionEnd(thisObject, clazz.getName(),
						method.getName(), args, r.getE());
			}
		} catch (MethodInvokeException e) {
			Profiler.exceptionEnd(thisObject, clazz.getName(),
					method.getName(), args, e);
			throw e;
		}
	}

	public void declareArgs(ArgDef argDef) {
		argDef.setCommandName("invoke");
		argDef.setDef("[-f <filter-expression>] [-t] <invoke-expression>");
		argDef.setDesc("调用某个类的静态方法，或者目标对象的某个实例方法");
		argDef.addOptionDesc(
				"invoke-expression",
				"调用静态方法可以输入 'package.ClassName.methodName(param1,param2)' , 调用实例方法可以输入 'methodName(param1,param2)' . 参数可以使用json格式.");
		argDef.addOptionDesc("-f",
				"设置 <filter-expression> 表达式，可以过滤掉不需要输出的调用细节。");
		argDef.addOptionDesc("-t", "是否需要跟踪调用的内部细节。");

	}

}
