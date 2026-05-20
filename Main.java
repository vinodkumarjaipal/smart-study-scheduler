import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class Main {
    private static final int PORT = 8080;
    private static final String USER_FILE = "users.txt";

    public static void main(String[] args) throws IOException {
        // Init user storage file if not exists
        File file = new File(USER_FILE);
        if (!file.exists()) {
            file.createNewFile();
        }

        // Initialize high-performance light HTTP server
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        
        // Routing/Endpoints configuration
        server.createContext("/api/signup", new SignupHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/optimize", new OptimizationHandler());
        
        // Default executor uses current thread engine
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10)); 
        System.out.println("Advanced AoA Backend Server running on port " + PORT);
        server.start();
    }

    
    // ALGORITHM: WEIGHTED INTERVAL SCHEDULING USING DYNAMIC PROGRAMMING
    
    public static List<StudySession> computeOptimalSchedule(List<StudySession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return new ArrayList<>();
        }

        // Step 1: Sort by End Time (Automatically works because of Comparable in Record)
        Collections.sort(sessions);
        int n = sessions.size();

        // Step 2: Create DP table to store maximum priority at each step
        int[] dp = new int[n + 1];
        dp[0] = 0;

        // Step 3: Compute values using Binary Search for efficiency O(n log n)
        for (int i = 1; i <= n; i++) {
            int currentPriority = sessions.get(i - 1).priority();
            int prevNonOverlappingIdx = findLatestNonOverlapping(sessions, i - 1);
            
            int includePriority = currentPriority + (prevNonOverlappingIdx != -1 ? dp[prevNonOverlappingIdx + 1] : 0);
            int excludePriority = dp[i - 1];

            dp[i] = Math.max(includePriority, excludePriority);
        }

        // Step 4: Backtracking to trace and fetch the exact chosen sessions
        List<StudySession> optimalPlan = new ArrayList<>();
        int i = n;
        while (i > 0) {
            int currentPriority = sessions.get(i - 1).priority();
            int prevNonOverlappingIdx = findLatestNonOverlapping(sessions, i - 1);
            
            int includePriority = currentPriority + (prevNonOverlappingIdx != -1 ? dp[prevNonOverlappingIdx + 1] : 0);
            
            if (includePriority >= dp[i - 1]) {
                optimalPlan.add(sessions.get(i - 1)); // Target acquired
                i = prevNonOverlappingIdx + 1;       // Jump to the non-overlapping profile
            } else {
                i--; // Excluded, move to previous index
            }
        }

        // Reverse to maintain chronological order
        Collections.reverse(optimalPlan);
        return optimalPlan;
    }

    // Textbook Standard Binary Search
    private static int findLatestNonOverlapping(List<StudySession> sessions, int index) {
        int low = 0, high = index - 1;
        int targetStartTime = sessions.get(index).startTime();
        int result = -1; // Default indicator if no non-overlapping session exists

        while (low <= high) {
            int mid = (low + high) >>> 1;
            
            if (sessions.get(mid).endTime() <= targetStartTime) {
                result = mid;     // Valid candidate found, save its index
                low = mid + 1;    // Search right to find if there's a later non-overlapping session
            } else {
                high = mid - 1;   // Overlap detected, search left
            }
        }
        return result;
    }

    // HTTP ROUTE HANDLERS
    
    static class SignupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String username = extractJsonValue(body, "username");
                String password = extractJsonValue(body, "password");

                if (username.isEmpty() || password.isEmpty()) {
                    sendResponse(exchange, "{\"status\":\"error\",\"message\":\"Fields cannot be empty\"}", 400);
                    return;
                }

                synchronized (Main.class) {
                    if (isUserExists(username)) {
                        sendResponse(exchange, "{\"status\":\"error\",\"message\":\"Username already registered\"}", 409);
                    } else {
                        saveUser(new User(username, password));
                        sendResponse(exchange, "{\"status\":\"success\",\"message\":\"Account created successfully\"}", 201);
                    }
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String username = extractJsonValue(body, "username");
                String password = extractJsonValue(body, "password");

                if (validateUser(username, password)) {
                    sendResponse(exchange, "{\"status\":\"success\",\"message\":\"Authentication successful\"}", 200);
                } else {
                    sendResponse(exchange, "{\"status\":\"error\",\"message\":\"Invalid credentials\"}", 401);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    static class OptimizationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                List<StudySession> inputSessions = parseSessionsFromJson(body);
                
                // Fire algorithm
                List<StudySession> optimizedPlan = computeOptimalSchedule(inputSessions);
                
                // Convert response back to JSON array string
                StringBuilder jsonResponse = new StringBuilder("[");
                for (int i = 0; i < optimizedPlan.size(); i++) {
                    StudySession s = optimizedPlan.get(i);
                    jsonResponse.append(String.format(
                        "{\"subjectName\":\"%s\",\"startTime\":%d,\"endTime\":%d,\"priority\":%d}",
                        s.subjectName(), s.startTime(), s.endTime(), s.priority()
                    ));
                    if (i < optimizedPlan.size() - 1) jsonResponse.append(",");
                }
                jsonResponse.append("]");

                sendResponse(exchange, jsonResponse.toString(), 200);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        }
    }

    // DATA LAYER & UTILITIES

    private static synchronized void saveUser(User user) throws IOException {
        Files.writeString(Paths.get(USER_FILE), user.toFileString() + System.lineSeparator(), StandardOpenOption.APPEND);
    }

    private static boolean isUserExists(String username) throws IOException {
        return Files.readAllLines(Paths.get(USER_FILE)).stream()
                .map(User::fromFileString)
                .filter(Objects::nonNull)
                .anyMatch(u -> u.username().equalsIgnoreCase(username));
    }

    private static boolean validateUser(String username, String password) throws IOException {
        return Files.readAllLines(Paths.get(USER_FILE)).stream()
                .map(User::fromFileString)
                .filter(Objects::nonNull)
                .anyMatch(u -> u.username().equals(username) && u.password().equals(password));
    }

    // High performance Regex based JSON extractor to bypass external heavy frameworks
    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\":\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    // Advanced Regex tokenizer to map JSON arrays into StudySesion Records natively
    private static List<StudySession> parseSessionsFromJson(String json) {
        List<StudySession> list = new ArrayList<>();
        Pattern objectPattern = Pattern.compile("\\{[^\\}]+\\}");
        Matcher matcher = objectPattern.matcher(json);

        while (matcher.find()) {
            String objStr = matcher.group();
            String name = extractJsonValue(objStr, "subjectName");
            
            // Dig out integers safely
            int start = fetchIntFromJson(objStr, "startTime");
            int end = fetchIntFromJson(objStr, "endTime");
            int priority = fetchIntFromJson(objStr, "priority");

            list.add(new StudySession(name, start, end, priority));
        }
        return list;
    }

    private static int fetchIntFromJson(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\":\\s*(\\d+)");
        Matcher m = p.matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().set("Content-Type", "application/json");
    }

    private static void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}