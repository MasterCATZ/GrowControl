package com.growcontrol.server;

import java.io.PrintStream;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;

import com.growcontrol.common.scripting.gcScriptManager;
import com.growcontrol.server.commands.gcServerCommands;
import com.growcontrol.server.configs.gcServerConfig;
import com.growcontrol.server.net.NetServerManager;
import com.poixson.commonapp.app.xApp;
import com.poixson.commonapp.app.annotations.xAppStep;
import com.poixson.commonapp.app.annotations.xAppStep.StepType;
import com.poixson.commonapp.config.xConfigLoader;
import com.poixson.commonapp.plugin.xPluginManager;
import com.poixson.commonjava.Failure;
import com.poixson.commonjava.xVars;
import com.poixson.commonjava.EventListener.xHandler;
import com.poixson.commonjava.Utils.utils;
import com.poixson.commonjava.Utils.utilsString;
import com.poixson.commonjava.Utils.xTime;
import com.poixson.commonjava.scheduler.ticker.xTickPrompt;
import com.poixson.commonjava.scheduler.ticker.xTicker;
import com.poixson.commonjava.xLogger.xLevel;
import com.poixson.commonjava.xLogger.xLog;


public class gcServer extends xApp {

	// config
	private volatile gcServerConfig config = null;



	/**
	 * Get the server class instance.
	 * @return
	 */
	public static gcServer get() {
		return (gcServer) xApp.get();
	}



	/**
	 * Application start entry point.
	 * @param args Command line arguments.
	 */
	public static void main(final String[] args) {
		initMain(args, gcServer.class);
	}
	public gcServer() {
		super();
		gcServerVars.init();
		if(xVars.debug())
			this.displayColors();
		this.displayLogo();
	}



	// init config
	@Override
	protected void initConfig() {
		this.config = (gcServerConfig) xConfigLoader.Load(
			gcServerConfig.CONFIG_FILE,
			gcServerConfig.class
		);
		if(this.config == null) {
			Failure.fail("Failed to load "+gcServerConfig.CONFIG_FILE);
			return;
		}
		if(this.config.isFromResource())
			log().warning("Created default "+gcServerDefines.CONFIG_FILE);
		this.updateConfig();
	}
	private void updateConfig() {
		// config version
		{
			boolean configVersionDifferent = false;
			final String configVersion = this.config.getVersion();
			final String serverVersion = this.getVersion();
			if(utils.notEmpty(configVersion) && utils.notEmpty(serverVersion)) {
				if(configVersion.endsWith("x") || configVersion.endsWith("*")) {
					final String vers = utilsString.trims(configVersion, "x", "*");
					if(!serverVersion.startsWith(vers))
						configVersionDifferent = true;
				} else {
					if(!configVersion.equals(serverVersion))
						configVersionDifferent = true;
				}
			}
			if(configVersionDifferent)
				log().warning(gcServerDefines.CONFIG_FILE+" for this server may need updates");
		}
		// log level
		{
			final Boolean debug = this.config.getDebug();
			if(debug != null && debug.booleanValue())
				xVars.debug(debug.booleanValue());
			if(!xVars.debug()) {
				// set log level
				final xLevel level = this.config.getLogLevel();
				if(level != null)
					xLog.getRoot().setLevel(level);
			}
		}
		// tick interval
		{
			final xTime tick = this.config.getTickInterval();
			xTicker.get()
				.setInterval(tick);
		}
//		// logic threads (0 uses main thread)
//		{
//			@SuppressWarnings("unused")
//			final int logic = this.config.getLogicThreads();
//			//TODO: apply this to logic thread pool
//		}
//		// zones
//		{
//			this.config.populateZones(this.zones);
//		}
		// sockets
		{
			final NetServerManager manager = NetServerManager.get();
			manager.setConfigs(this.config.getSocketConfigs());
		}
	}



	/**
	 * Handle command-line arguments.
	 */
	@Override
	protected void processArgs(final String[] args) {
		if(utils.isEmpty(args)) return;
		for(final String arg : args) {
			switch(arg) {
			case "--debug":
				xVars.debug(true);
				break;
			default:
				System.out.println("Unknown argument: "+arg);
				System.exit(1);
				break;
			}
		}
	}



	// ------------------------------------------------------------------------------- //
	// startup



	// command prompt
	@xAppStep(type=StepType.STARTUP, title="Commands", priority=30)
	public void _startup_commands_() {
		final xHandler handler = gcServerVars.commands();
		handler.register(
				new gcServerCommands()
		);
		xLog.setCommandHandler(
				handler
		);
	}

	// console input
	@xAppStep(type=StepType.STARTUP, title="Console", priority=32)
	public void _startup_console_() {
		xLog.getConsole()
			.Start();
		// prompt ticker
		if(this.config.getPromptTickerEnabled())
			new xTickPrompt();
	}

//	// io event listener
//	@xAppStep(type=StepType.STARTUP, title="ioEvents", priority=40)
//	public void _startup_ioevents_() {
//		getLogicQueue();
//	}

	// load plugins
	@xAppStep(type=StepType.STARTUP, title="LoadPlugins", priority=50)
	public void _startup_load_plugins_() {
		final xPluginManager manager = xPluginManager.get();
		manager.setClassField("Server Main");
		manager.loadAll();
		manager.initAll();
	}

	// enable plugins
	@xAppStep(type=StepType.STARTUP, title="LoadPlugins", priority=55)
	public void _startup_enable_plugins_() {
		xPluginManager.get()
			.enableAll();
	}

	// sockets
	@xAppStep(type=StepType.STARTUP, title="Sockets", priority=90)
	public void _startup_sockets_() {
		NetServerManager.get()
			.Start();
	}

	// scripts
	@xAppStep(type=StepType.STARTUP, title="Scripts", priority=95)
	public void _startup_scripts_() {
		final gcScriptManager manager = gcScriptManager.get();
		manager.loadAll();
		manager.StartAll();
	}



//		// load zones
//		synchronized(zones) {
//			config.PopulateZones(zones);
//			log.info("Loaded [ "+Integer.toString(this.zones.size())+" ] zones.");
//		}
//		// start logic thread queue
//		getLogicQueue();
//
//		// start socket listener
//		if(socket == null)
//			socket = new pxnSocketServer();
//		socket.setHost();
//		socket.setPort(ServerConfig.ListenPort());
//		// create processor
//		socket.setFactory(new pxnSocketProcessorFactory() {
//			@Override
//			public gcPacketReader newProcessor() {
//				return new gcPacketReader();
//			}
//		});
//		socket.Start();



	// ------------------------------------------------------------------------------- //
	// shutdown



	// scripts
	@xAppStep(type=StepType.SHUTDOWN, title="Scripts", priority=95)
	public void _shutdown_scripts_() {
		gcScriptManager.get()
			.StopAll();
	}

	// sockets
	@xAppStep(type=StepType.SHUTDOWN, title="Sockets", priority=90)
	public void _shutdown_sockets_() {
		final NetServerManager manager = NetServerManager.get();
		manager.Stop();
		manager.CloseAll();
	}

	// disable plugins
	@xAppStep(type=StepType.SHUTDOWN, title="DisablePlugins", priority=55)
	public void _shutdown_disable_plugins_() {
		xPluginManager.get()
			.disableAll();
	}

	// unload plugins
	@xAppStep(type=StepType.SHUTDOWN, title="UnloadPlugins", priority=50)
	public void _shutdown_unload_plugins_() {
		xPluginManager.get()
			.unloadAll();
	}



	// ------------------------------------------------------------------------------- //



	public gcServerConfig getConfig() {
		return this.config;
	}



//		pxnLogger log = pxnLog.get();
//if(!consoleEnabled) {
//System.out.println("Console input is disabled due to noconsole command argument.");
////TODO: currently no way to stop the server with no console input
//System.exit(0);
//}


//TODO: remove this
//log.severe("Listing Com Ports:");
//for(Map.Entry<String, String> entry : Serial.listPorts().entrySet())
//log.severe(entry.getKey()+" - "+entry.getValue());


//TODO: remove temp scheduled task
// new task (multi-threaded / repeat)
//pxnSchedulerTask task = new pxnSchedulerTask(true, true) {
//	@Override
//	public void run() {
//		pxnLog.get().Publish("333333333 tick");
//	}
//	@Override
//	public String getTaskName() {
//		return "333tickname";
//	}
//};
//task.addTrigger(new triggerInterval("3s"));
//pxnScheduler.get("gcServer").newTask(task);

//System.out.println("next run: "+task.UntilNextTrigger().get(TimeU.MS));


//	// get zones
//	public List<String> getZones() {
//		synchronized(zones) {
//			return new ArrayList<String>(zones);
//		}
//	}
//	public String[] getZonesArray() {
//		synchronized(zones) {
//			return (String[]) zones.toArray();
//		}
//	}



	// ascii header
	@Override
	protected void displayLogo() {
		final PrintStream out = AnsiConsole.out;
		final Ansi.Color bgcolor = Ansi.Color.BLACK;
		out.println();
		// line 1
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).bold().a("      ")
			.fg(Ansi.Color.GREEN).a("P")
			.fg(Ansi.Color.WHITE).a("oi")
			.fg(Ansi.Color.GREEN).a("X")
			.fg(Ansi.Color.WHITE).a("son")
			.a("                          ")
			.a("                          ")
			.reset() );
		// line 2
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).bold().a("   ")
			.fg(Ansi.Color.BLACK).a("©")
			.fg(Ansi.Color.GREEN).a("GROW")
			.fg(Ansi.Color.WHITE).a("CONTROL")
			.boldOff().a("     ")
			/* C */.fg(Ansi.Color.YELLOW).bold().a("_")
			.a("                      ")
			.a("                      ")
			.reset() );
		// line 3
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("      ")
			.fg(Ansi.Color.CYAN).a("Server      ")
			/* C */.fg(Ansi.Color.YELLOW).bold()
					.a("_(_)_").boldOff()
					.a("                          ")
			/* G */.fg(Ansi.Color.MAGENTA).a("wWWWw   ")
			/* H */.fg(Ansi.Color.YELLOW).bold().a("_")
			.a("       ")
			.reset() );
		// line 4
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("                 ")
			/* C */.fg(Ansi.Color.YELLOW).bold()
					.a("(_)@(_)   ").boldOff()
			/* D */.fg(Ansi.Color.MAGENTA).a("vVVVv     ")
			/* E */.fg(Ansi.Color.YELLOW).bold()
					.a("_     ").boldOff()
			/* F */.fg(Ansi.Color.BLUE).a("@@@@  ")
			/* G */.fg(Ansi.Color.MAGENTA).a("(___) ")
			/* H */.fg(Ansi.Color.YELLOW).bold().a("_(_)_")
			.a("     ")
			.reset() );
		// line 5
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("      ")
			/* A */.fg(Ansi.Color.RED).a("@@@@  ")
			/* B */.fg(Ansi.Color.MAGENTA).bold()
					.a("wWWWw  ").boldOff()
			/* C */.fg(Ansi.Color.YELLOW).bold()
					.a("(_)").boldOff()
					.fg(Ansi.Color.GREEN).a("\\    ")
			/* D */.fg(Ansi.Color.MAGENTA).a("(___)   ")
			/* E */.fg(Ansi.Color.YELLOW).bold()
					.a("_(_)_  ").boldOff()
			/* F */.fg(Ansi.Color.BLUE).a("@@()@@   ")
			/* F */.fg(Ansi.Color.MAGENTA).a("Y  ")
			/* H */.fg(Ansi.Color.YELLOW)
					.bold().a("(_)@(_)")
			.a("    ")
			.reset() );
		// line 6
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("     ")
			/* A */.fg(Ansi.Color.RED).a("@@()@@ ")
			/* B */.fg(Ansi.Color.MAGENTA).bold()
					.a("(___)     ").boldOff()
			/* C */.fg(Ansi.Color.GREEN).a("`|/    ")
			/* D */.fg(Ansi.Color.MAGENTA).a("Y    ")
			/* E */.fg(Ansi.Color.YELLOW).bold()
					.a("(_)@(_)  ").boldOff()
			/* F */.fg(Ansi.Color.BLUE).a("@@@@   ")
			/* G */.fg(Ansi.Color.GREEN).a("\\|/   ")
			/* H */.fg(Ansi.Color.YELLOW).bold()
					.a("(_)").boldOff()
					.fg(Ansi.Color.GREEN).a("\\")
			.a("     ")
			.reset() );
		// line 7
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("      ")
			/* A */.fg(Ansi.Color.RED).a("@@@@    ")
			/* B */.fg(Ansi.Color.MAGENTA).a("Y       ")
			/* C */.fg(Ansi.Color.GREEN).a("\\|    ")
			/* D */.fg(Ansi.Color.GREEN).a("\\|/    ")
			/* E */.fg(Ansi.Color.GREEN).a("/")
					.fg(Ansi.Color.YELLOW).bold()
					.a("(_)    ").boldOff()
			/* F */.fg(Ansi.Color.GREEN).a("\\|      ")
			/* G */.fg(Ansi.Color.GREEN).a("|/      ")
			/* H */.fg(Ansi.Color.GREEN).a("|     ")
			.reset() );
		// line 8
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("    ")
			.fg(Ansi.Color.GREEN)
			/* A */.a("\\  /    ")
			/* B */.a("\\ |/       ")
			/* C */.a("| / ")
			/* D */.a("\\ | /  ")
			/* E */.a("\\|/       ")
			/* F */.a("|/    ")
			/* G */.a("\\|      ")
			/* H */.a("\\|/    ")
			.reset() );
		// line 9
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor).a("    ")
			.fg(Ansi.Color.GREEN)
			/* A */.a("\\\\|//   ")
			/* B */.a("\\\\|///  ")
			/* C */.a("\\\\\\|//")
			/* D */.a("\\\\\\|/// ")
			/* E */.a("\\|///  ")
			/* F */.a("\\\\\\|//  ")
			/* G */.a("\\\\|//  ")
			/* H */.a("\\\\\\|//   ")
			.reset() );
		// line 10
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor)
			.fg(Ansi.Color.GREEN)
			.a("^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/")
			.a("^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^")
			.reset() );
		// line 11
		out.println(Ansi.ansi()
			.a(" ").bg(bgcolor)
			.fg(Ansi.Color.GREEN)
			.a("////////////////////////////////")
			.a("/////////////////////////////////")
			.reset() );
		out.println();
		out.println(" Copyright (C) 2007-2014 PoiXson, Mattsoft");
		out.println(" - Brainchild of the one known as lorenzo -");
		out.println(" This program comes with absolutely no warranty. This is free software");
		out.println(" and you are welcome to modify it or redistribute it under certain");
		out.println(" conditions. Type 'show license' for license details.");
		out.println();
		out.flush();
	}

//            A      B      C       D      E       F      G      H
// 1 |       PoiXson                                                     |
// 2 |    ©GROWCONTROL     _                                             |
// 3 |       Server      _(_)_                          wWWWw   _        |
// 4 |                  (_)@(_)   vVVVv     _     @@@@  (___) _(_)_      |
// 5 |       @@@@  wWWWw  (_)\    (___)   _(_)_  @@()@@   Y  (_)@(_)     |
// 6 |      @@()@@ (___)     `|/    Y    (_)@(_)  @@@@   \|/   (_)\      |
// 7 |       @@@@    Y       \|    \|/    /(_)    \|      |/      |      |
// 8 |     \  /    \ |/       | / \ | /  \|/       |/    \|      \|/     |
// 9 |     \\|//   \\|///  \\\|//\\\|/// \|///  \\\|//  \\|//  \\\|//    |
//10 | ^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^/^ |
//11 | ///////////////////////////////////////////////////////////////// |
//   0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8 0 2 4 6 8
//   0         1         2         3         4         5         6

//System.out.println("                     .==IIIIIIIIIIII=:.");
//System.out.println("               .7IIII777777II7I7I77777III.");
//System.out.println(" .+?IIII7IIIIII7777I++III+I+++?+I77IIII777II");
//System.out.println(" .II=+?7777777II?+==+====?+=++?7++++?I?I7777II");
//System.out.println("  ~II=III+?=+=======+==III=?+==+I??+?+7???II77I.");
//System.out.println("   +I7?==+===?=III+?I=?===+=?=?I??=?+++??III?I77=");
//System.out.println("     I77II+=+=7=?======?=?~+=I====?7+=???+++II?7I7        .II7I7=.");
//System.out.println("      II7I7+===I=?I+++++~=~+7~~=??I=I=+=++?7++??777...7I7I7??7++7I77IIIIIIIII");
//System.out.println("        II7+I77I+=~?7=I~?7I=?7I~~+=++7=I===+II++77I7777I??77I++=?7I===+?7?+=7I");
//System.out.println("          II7++~+I7I77777777777777I?=~77+=I+?=++I7777??I?+++=?I?===77I+=+7+7:");
//System.out.println("            ~IIIII+==+~~~~+:?~=:~~+II77777??=I+?+777I??+=7?=?=II==I==I+?77=");
//System.out.println("                IIIIII7777??+=::~?:~~~=I=I777=?I??7?+I7?7+~7777=+77777?+I");
//System.out.println("                    :IIIIIIII7777777I:?~~~+~I77I=?I=I+77?=?::=~??+?777I");
//System.out.println("                             IIIIIII7777+~~?~=+777?I77?~~77777II,");
//System.out.println("                                  ~7III777?:~~?~777I~:?77II");
//System.out.println("                                      ~III77~~~++7~I=77");
//System.out.println("                                         :II7I:::I7:7I.");
//System.out.println("                                           ~I77:~~:I7II");
//System.out.println("                                            +I77:::I7II");
//System.out.println("                                             II7,,::77II");
//System.out.println("                            PoiXson           I7?~~,=7I");
//System.out.println("                          ©GROWCONTROL         I7?,:III");
//System.out.println("                                                ~III+.");



}
