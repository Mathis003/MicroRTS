package tournaments;

import ai.core.AI;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import rts.units.UnitTypeTable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Command-line interface to run tournaments with JSON configuration or command-line parameters
 * 
 * Usage:
 *   java -cp "bin:lib/*" tournaments.RunTournamentCLI --config <config.json>
 *   java -cp "bin:lib/*" tournaments.RunTournamentCLI <tournament_folder> <maps> <AIs> [jar_folder] [options]
 * 
 * Examples:
 *   java -cp "bin:lib/*" tournaments.RunTournamentCLI --config tournament_config.json
 *   java -cp "bin:lib/*" tournaments.RunTournamentCLI tournament_1 maps/8x8/basesWorkers8x8.xml WorkerRush,LightRush
 *   java -cp "bin:lib/*" tournaments.RunTournamentCLI tournament_1 maps/8x8/basesWorkers8x8.xml WorkerRush,LightRush lib/bots --iterations=10 --maxGameLength=5000
 */
public class RunTournamentCLI {
    
    private static Map<String, Class> loadedBotClasses = new HashMap<>();
    private static int totalGames = 0;
    private static int completedGames = 0;
    
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                printUsage();
                System.exit(1);
            }
            
            TournamentConfig config;
            
            // Check if using JSON config
            if (args[0].equals("--config")) {
                if (args.length < 2) {
                    System.err.println("ERROR: --config requires a JSON file path");
                    System.exit(1);
                }
                config = loadFromJson(args[1]);
            } else {
                // Command line mode
                if (args.length < 3) {
                    printUsage();
                    System.exit(1);
                }
                config = loadFromCommandLine(args);
            }
            
            runTournament(config);
            
        } catch (Exception e) {
            System.err.println("FATAL ERROR: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static TournamentConfig loadFromJson(String jsonFile) throws Exception {
        System.out.println("Loading configuration from: " + jsonFile);
        
        // Check file exists
        if (!Files.exists(Paths.get(jsonFile))) {
            throw new FileNotFoundException("Configuration file not found: " + jsonFile);
        }
        
        String content = new String(Files.readAllBytes(Paths.get(jsonFile)));
        Gson gson = new Gson();
        JsonObject json;
        
        try {
            json = gson.fromJson(content, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }
        
        if (json == null) {
            throw new IllegalArgumentException("JSON file is empty or invalid");
        }
        
        // Validate required fields
        validateRequiredField(json, "tournamentFolder", "string");
        validateRequiredField(json, "maps", "array");
        validateRequiredField(json, "ais", "array");
        validateRequiredField(json, "iterations", "number");
        validateRequiredField(json, "maxGameLength", "number");
        validateRequiredField(json, "timeBudget", "number");
        validateRequiredField(json, "iterationsBudget", "number");
        validateRequiredField(json, "preAnalysisBudget", "number");
        validateRequiredField(json, "fullObservability", "boolean");
        validateRequiredField(json, "selfMatches", "boolean");
        validateRequiredField(json, "timeoutCheck", "boolean");
        validateRequiredField(json, "runGC", "boolean");
        validateRequiredField(json, "saveTraces", "boolean");
        
        TournamentConfig config = new TournamentConfig();
        config.tournamentFolder = json.get("tournamentFolder").getAsString();
        config.botJarsFolder = json.has("botJarsFolder") ? json.get("botJarsFolder").getAsString() : null;
        config.saveGameLogs = json.has("saveGameLogs") ? json.get("saveGameLogs").getAsBoolean() : true;
        
        // Load and validate arrays
        json.get("maps").getAsJsonArray().forEach(e -> config.maps.add(e.getAsString()));
        json.get("ais").getAsJsonArray().forEach(e -> config.ais.add(e.getAsString()));
        
        if (config.maps.isEmpty()) {
            throw new IllegalArgumentException("At least 1 map is required in 'maps' array");
        }
        
        if (config.ais.size() < 2) {
            throw new IllegalArgumentException("At least 2 AIs are required in 'ais' array (found: " + config.ais.size() + ")");
        }
        
        // Load required parameters
        config.iterations = json.get("iterations").getAsInt();
        config.maxGameLength = json.get("maxGameLength").getAsInt();
        config.timeBudget = json.get("timeBudget").getAsInt();
        config.iterationsBudget = json.get("iterationsBudget").getAsInt();
        config.preAnalysisBudget = json.get("preAnalysisBudget").getAsLong();
        config.fullObservability = json.get("fullObservability").getAsBoolean();
        config.selfMatches = json.get("selfMatches").getAsBoolean();
        config.timeoutCheck = json.get("timeoutCheck").getAsBoolean();
        config.runGC = json.get("runGC").getAsBoolean();
        config.saveTraces = json.get("saveTraces").getAsBoolean();
        
        // Validate numeric values
        if (config.iterations <= 0) {
            throw new IllegalArgumentException("iterations must be positive (found: " + config.iterations + ")");
        }
        if (config.maxGameLength <= 0) {
            throw new IllegalArgumentException("maxGameLength must be positive (found: " + config.maxGameLength + ")");
        }
        if (config.timeBudget <= 0) {
            throw new IllegalArgumentException("timeBudget must be positive (found: " + config.timeBudget + ")");
        }
        
        System.out.println("Configuration validated successfully");
        return config;
    }
    
    private static void validateRequiredField(JsonObject json, String fieldName, String expectedType) {
        if (!json.has(fieldName)) {
            throw new IllegalArgumentException("Missing required field: '" + fieldName + "'");
        }
        
        try {
            switch (expectedType) {
                case "string":
                    json.get(fieldName).getAsString();
                    break;
                case "number":
                    json.get(fieldName).getAsNumber();
                    break;
                case "boolean":
                    json.get(fieldName).getAsBoolean();
                    break;
                case "array":
                    if (!json.get(fieldName).isJsonArray()) {
                        throw new IllegalArgumentException("Field '" + fieldName + "' must be an array");
                    }
                    break;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Field '" + fieldName + "' has invalid type (expected: " + expectedType + ")");
        }
    }
    
    private static TournamentConfig loadFromCommandLine(String[] args) {
        TournamentConfig config = new TournamentConfig();
        config.tournamentFolder = args[0];
        
        // Parse maps
        for (String map : args[1].split(",")) {
            config.maps.add(map.trim());
        }
        
        // Parse AIs
        for (String ai : args[2].split(",")) {
            config.ais.add(ai.trim());
        }
        
        // Parse optional JAR folder
        int optionStart = 3;
        if (args.length > 3 && !args[3].startsWith("--")) {
            config.botJarsFolder = args[3];
            optionStart = 4;
        }
        
        // Parse options
        for (int i = optionStart; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String option = arg.substring(2);
                if (option.equals("selfMatches")) {
                    config.selfMatches = true;
                } else if (option.contains("=")) {
                    String[] parts = option.split("=", 2);
                    String key = parts[0];
                    String value = parts[1];
                    
                    switch (key) {
                        case "iterations":
                            config.iterations = Integer.parseInt(value);
                            break;
                        case "maxGameLength":
                            config.maxGameLength = Integer.parseInt(value);
                            break;
                        case "timeBudget":
                            config.timeBudget = Integer.parseInt(value);
                            break;
                        case "iterationsBudget":
                            config.iterationsBudget = Integer.parseInt(value);
                            break;
                    }
                }
            }
        }
        
        return config;
    }
    
    private static void runTournament(TournamentConfig config) throws Exception {
        // Save original streams
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        
        // Load JARs if specified
        if (config.botJarsFolder != null && !config.botJarsFolder.isEmpty()) {
            System.out.println("Loading bot JARs from: " + config.botJarsFolder);
            
            File jarFolder = new File(config.botJarsFolder);
            if (!jarFolder.exists()) {
                throw new FileNotFoundException("Bot JARs folder not found: " + config.botJarsFolder);
            }
            if (!jarFolder.isDirectory()) {
                throw new IllegalArgumentException("Bot JARs path is not a directory: " + config.botJarsFolder);
            }
            
            // Create temp file for JAR loading logs
            File tempDir = new File(config.tournamentFolder);
            tempDir.mkdirs();
            PrintStream jarLoadLog = new PrintStream(new FileOutputStream(config.tournamentFolder + "/jar_loading.log"));
            System.setOut(jarLoadLog);
            System.setErr(jarLoadLog);
            
            try {
                List<Class> jarClasses = LoadTournamentAIs.loadTournamentAIsFromFolder(config.botJarsFolder);
                for (Class c : jarClasses) {
                    loadedBotClasses.put(c.getSimpleName(), c);
                }
                
                // Restore output
                System.setOut(originalOut);
                System.setErr(originalErr);
                jarLoadLog.close();
                
                System.out.println("Loaded " + jarClasses.size() + " bot(s) from JARs");
            } catch (Exception e) {
                System.setOut(originalOut);
                System.setErr(originalErr);
                jarLoadLog.close();
                throw new RuntimeException("Failed to load bot JARs: " + e.getMessage(), e);
            }
        }
        
        System.out.println("\nMaps: " + config.maps.size());
        for (String m : config.maps) {
            System.out.println("  - " + m);
            // Validate map file exists
            if (!new File(m).exists()) {
                throw new FileNotFoundException("Map file not found: " + m);
            }
        }
        
        // Load AIs
        UnitTypeTable utt = new UnitTypeTable();
        System.out.println("\nLoading AIs: " + config.ais.size());
        
        // Create tournament folder if needed
        File tournamentDir = new File(config.tournamentFolder);
        if (!tournamentDir.exists()) {
            if (!tournamentDir.mkdirs()) {
                throw new IOException("Failed to create tournament folder: " + config.tournamentFolder);
            }
        }
        
        // Redirect output during AI loading
        PrintStream aiLoadLog = new PrintStream(new FileOutputStream(config.tournamentFolder + "/ai_loading.log"));
        System.setOut(aiLoadLog);
        System.setErr(aiLoadLog);
        
        List<AI> ais = new ArrayList<>();
        try {
            for (String aiName : config.ais) {
                try {
                    AI ai = loadAI(aiName, utt);
                    ais.add(ai);
                } catch (Exception e) {
                    System.setOut(originalOut);
                    System.setErr(originalErr);
                    aiLoadLog.close();
                    throw new RuntimeException("Failed to load AI '" + aiName + "': " + e.getMessage(), e);
                }
            }
            
            // Restore output
            System.setOut(originalOut);
            System.setErr(originalErr);
            aiLoadLog.close();
            
            for (AI ai : ais) {
                System.out.println("  Loaded: " + ai.getClass().getSimpleName());
            }
        } catch (Exception e) {
            System.setOut(originalOut);
            System.setErr(originalErr);
            aiLoadLog.close();
            throw e;
        }
        
        // Tournament folder already created above
        // Calculate total games for progress tracking
        int numAIs = ais.size();
        int matchups = config.selfMatches ? (numAIs * numAIs) : (numAIs * (numAIs - 1));
        totalGames = config.iterations * config.maps.size() * matchups;
        completedGames = 0;
        
        // Create writers
        String csvFile = config.tournamentFolder + "/tournament.csv";
        Writer writer = new CSVTrackingWriter(
            new BufferedWriter(new FileWriter(csvFile)),
            originalOut,  // Pass original output for progress tracking
            totalGames
        );
        Writer progress = new BufferedWriter(new FileWriter(config.tournamentFolder + "/progress.log"));

        System.out.println("\nTournament Configuration:");
        System.out.println("  Iterations per matchup: " + config.iterations);
        System.out.println("  Max game length: " + config.maxGameLength + " frames");
        System.out.println("  Time budget: " + config.timeBudget + " ms");
        System.out.println("  Iterations budget: " + config.iterationsBudget);
        System.out.println("  Pre-analysis budget: " + config.preAnalysisBudget + " ms");
        System.out.println("  Full observability: " + config.fullObservability);
        System.out.println("  Self matches: " + config.selfMatches);
        System.out.println("  Timeout check: " + config.timeoutCheck);
        System.out.println("  Run GC: " + config.runGC);
        System.out.println("  Save traces: " + config.saveTraces);
        System.out.println("\nTotal games to play: " + totalGames);
        System.out.println("\nStarting tournament...\n");
        
        // Redirect System.out and System.err to suppress all game logs
        PrintStream gameLogStream = null;
        PrintStream errorLogStream = null;
        
        if (config.saveGameLogs) {
            gameLogStream = new PrintStream(new FileOutputStream(config.tournamentFolder + "/game_logs.txt"));
            errorLogStream = new PrintStream(new FileOutputStream(config.tournamentFolder + "/error_logs.txt"));
        } else {
            // Redirect to null stream (discard all output)
            gameLogStream = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) {}
            });
            errorLogStream = gameLogStream;
        }
        
        System.setOut(gameLogStream);
        System.setErr(errorLogStream);
        
        // Run tournament
        try {
            new RoundRobinTournament(ais).runTournament(
                -1,
                config.maps,
                config.iterations,
                config.maxGameLength,
                config.timeBudget,
                config.iterationsBudget,
                config.preAnalysisBudget,
                config.preAnalysisBudget,
                config.fullObservability,
                config.selfMatches,
                config.timeoutCheck,
                config.runGC,
                true,
                utt,
                config.saveTraces ? config.tournamentFolder + "/traces" : null,
                writer,
                progress,
                config.tournamentFolder
            );
        } finally {
            // Restore System.out and System.err
            System.setOut(originalOut);
            System.setErr(System.err);
            gameLogStream.close();
            errorLogStream.close();
        }
        
        writer.close();
        progress.close();
        
        System.out.println("\n============================================================");
        System.out.println("Tournament completed successfully!");
        System.out.println("Results saved to: " + csvFile);
        
        if (config.saveGameLogs) {
            // Check log file sizes and warn if too large
            File gameLogFile = new File(config.tournamentFolder + "/game_logs.txt");
            File errorLogFile = new File(config.tournamentFolder + "/error_logs.txt");
            long gameLogSize = gameLogFile.length() / (1024 * 1024); // Size in MB
            long errorLogSize = errorLogFile.length() / (1024 * 1024); // Size in MB
            
            if (gameLogSize > 0) {
                System.out.println("Game logs saved to: " + config.tournamentFolder + "/game_logs.txt (" + gameLogSize + " MB)");
            }
            if (errorLogSize > 0) {
                System.out.println("Error logs saved to: " + config.tournamentFolder + "/error_logs.txt (" + errorLogSize + " MB)");
            }
            if (gameLogSize > 100 || errorLogSize > 100) {
                System.out.println("WARNING: Log files are very large. Consider disabling game logs with 'saveGameLogs: false'.");
            }
        }
        
        System.out.println("============================================================");
    }
    
    private static AI loadAI(String aiName, UnitTypeTable utt) throws Exception {
        String className = aiName.contains("(") ? aiName.substring(0, aiName.indexOf('(')) : aiName;
        
        // Try loaded JARs first
        if (loadedBotClasses.containsKey(className)) {
            Class c = loadedBotClasses.get(className);
            try {
                return (AI) c.getConstructor(UnitTypeTable.class).newInstance(utt);
            } catch (NoSuchMethodException e) {
                return (AI) c.newInstance();
            }
        }
        
        // Try built-in packages
        String[] packages = {"ai.abstraction.", "ai.", "ai.core.", "ai.portfolio.", "ai.mcts.", "ai.ahtn.", "ai.rai."};
        for (String pkg : packages) {
            try {
                Class<?> clazz = Class.forName(pkg + className);
                try {
                    return (AI) clazz.getConstructor(UnitTypeTable.class).newInstance(utt);
                } catch (NoSuchMethodException e) {
                    return (AI) clazz.newInstance();
                }
            } catch (ClassNotFoundException e) {
                // Try next package
            }
        }
        
        throw new ClassNotFoundException("Could not find AI: " + aiName);
    }
    
    private static void printUsage() {
        System.err.println("Usage:");
        System.err.println("  java -cp \"bin:lib/*\" tournaments.RunTournamentCLI --config <config.json>");
        System.err.println("  java -cp \"bin:lib/*\" tournaments.RunTournamentCLI <folder> <maps> <AIs> [jar_folder] [options]");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  java -cp \"bin:lib/*\" tournaments.RunTournamentCLI --config tournament_example.json");
        System.err.println("  java -cp \"bin:lib/*\" tournaments.RunTournamentCLI tournament_1 maps/8x8/basesWorkers8x8.xml WorkerRush,LightRush --iterations=10");
    }
    
    /**
     * CSV tracking writer that updates progress based on result lines written
     */
    static class CSVTrackingWriter extends Writer {
        private final Writer delegate;
        private final PrintStream consoleOut;
        private final int totalGames;
        private int completedGames = 0;
        private int lastReportedGames = 0;
        private static final int REPORT_INTERVAL = 10; // Report every 10 games
        private StringBuilder lineBuffer = new StringBuilder();
        
        public CSVTrackingWriter(Writer delegate, PrintStream consoleOut, int totalGames) {
            this.delegate = delegate;
            this.consoleOut = consoleOut;
            this.totalGames = totalGames;
        }
        
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            delegate.write(cbuf, off, len);
            
            // Buffer characters to detect complete lines
            for (int i = off; i < off + len; i++) {
                char c = cbuf[i];
                lineBuffer.append(c);
                
                if (c == '\n') {
                    String line = lineBuffer.toString().trim();
                    lineBuffer = new StringBuilder();
                    
                    // Check if this is a result line (starts with number AND contains tabs)
                    // Format: iteration\tmap\tai1\tai2\ttime\twinner\tcrashed\ttimedout
                    if (!line.isEmpty() && Character.isDigit(line.charAt(0)) && line.contains("\t")) {
                        String[] parts = line.split("\t");
                        // Result lines have exactly 8 tab-separated values
                        if (parts.length >= 8) {
                            completedGames++;
                            updateProgress();
                        }
                    }
                }
            }
        }
        
        @Override
        public void write(String str) throws IOException {
            delegate.write(str);
            
            // Process the string for newlines
            for (char c : str.toCharArray()) {
                lineBuffer.append(c);
                
                if (c == '\n') {
                    String line = lineBuffer.toString().trim();
                    lineBuffer = new StringBuilder();
                    
                    // Check if this is a result line (starts with number AND contains tabs)
                    if (!line.isEmpty() && Character.isDigit(line.charAt(0)) && line.contains("\t")) {
                        String[] parts = line.split("\t");
                        if (parts.length >= 8) {
                            completedGames++;
                            updateProgress();
                        }
                    }
                }
            }
        }
        
        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
        
        @Override
        public void close() throws IOException {
            delegate.close();
        }
        
        private void updateProgress() {
            // Report every 10 games
            if (completedGames % REPORT_INTERVAL == 0 || completedGames >= totalGames) {
                double percentage = (completedGames * 100.0) / totalGames;
                consoleOut.printf("%d/%d (%.2f%%)\n", completedGames, totalGames, percentage);
                consoleOut.flush();
                lastReportedGames = completedGames;
            }
        }
    }
    
    /**
     * Progress tracking writer wrapper - kept for compatibility
     */
    static class ProgressTrackingWriter extends Writer {
        private final Writer delegate;
        private final PrintStream consoleOut;
        private final int totalIterations;
        private final int totalMaps;
        private int lastReportedGames = 0;
        private static final int REPORT_INTERVAL = 10; // Report every 10 games
        
        public ProgressTrackingWriter(Writer delegate, PrintStream consoleOut, int totalIterations, int totalMaps) {
            this.delegate = delegate;
            this.consoleOut = consoleOut;
            this.totalIterations = totalIterations;
            this.totalMaps = totalMaps;
        }
        
        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            delegate.write(cbuf, off, len);
            String content = new String(cbuf, off, len);
            
            // Detect game completion from progress log
            if (content.contains("winner") || content.contains("Game finished")) {
                completedGames++;
                updateProgress();
            }
        }
        
        @Override
        public void flush() throws IOException {
            delegate.flush();
        }
        
        @Override
        public void close() throws IOException {
            delegate.close();
        }
        
        private void updateProgress() {
            // Report every 10 games
            if (completedGames % REPORT_INTERVAL == 0 || completedGames >= totalGames) {
                double percentage = (completedGames * 100.0) / totalGames;
                consoleOut.printf("%d/%d (%.2f%%)\n", completedGames, totalGames, percentage);
                consoleOut.flush();
                lastReportedGames = completedGames;
            }
        }
    }
    
    static class TournamentConfig {
        String tournamentFolder;
        String botJarsFolder;
        List<String> maps = new ArrayList<>();
        List<String> ais = new ArrayList<>();
        int iterations = 5;
        int maxGameLength = 3000;
        int timeBudget = 100;
        int iterationsBudget = -1;
        long preAnalysisBudget = 1000;
        boolean fullObservability = true;
        boolean selfMatches = false;
        boolean timeoutCheck = true;
        boolean runGC = false;
        boolean saveTraces = false;
        boolean saveGameLogs = true;
    }
}