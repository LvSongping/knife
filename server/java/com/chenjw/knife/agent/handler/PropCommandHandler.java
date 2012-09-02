package com.chenjw.knife.agent.handler;

import com.chenjw.knife.agent.Agent;
import com.chenjw.knife.agent.CommandDispatcher;
import com.chenjw.knife.agent.CommandHandler;
import com.chenjw.knife.agent.args.ArgDef;
import com.chenjw.knife.agent.args.Args;

public class PropCommandHandler implements CommandHandler {

	public void handle(Args args, CommandDispatcher dispatcher) {
		if ("debug".equals(args.arg("type"))) {
			if ("on".equals(args.arg("status"))) {
				Agent.getAgentInfo().setDebugOn(true);
			} else {
				Agent.getAgentInfo().setDebugOn(false);
			}
		}
		Agent.info("finished!");
	}

	@Override
	public void declareArgs(ArgDef argDef) {
		argDef.setCommandName("prop");
		argDef.setDef("<type> <status>");
		argDef.setDesc("set field value to target object.");
		argDef.addOptionDesc("type", "debug");
		argDef.addOptionDesc("status", "on/off");

	}

}
