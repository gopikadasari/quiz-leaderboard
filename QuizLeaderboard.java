import java.net.URI;
import java.net.http.*;
import java.util.*;

public class QuizLeaderboard {

    static final String REG_NO = "RA2311003010379";
    static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        Set<String> seen = new HashSet<>();
        Map<String, Integer> scores = new HashMap<>();

        for (int poll = 0; poll <= 9; poll++) {
            System.out.println("Polling " + poll + "...");
            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            System.out.println("Response: " + body);

            if (!body.contains("\"events\"")) {
                System.out.println("No events in this response, skipping...");
                if (poll < 9) {
                    System.out.println("Waiting 5 seconds...");
                    Thread.sleep(5000);
                }
                continue;
            }

            int eventsStart = body.indexOf("\"events\"");
            String eventsSection = body.substring(eventsStart);
            int index = 0;
            while (true) {
                int roundStart = eventsSection.indexOf("\"roundId\"", index);
                if (roundStart == -1) break;
                int r1 = eventsSection.indexOf("\"", roundStart + 10) + 1;
                int r2 = eventsSection.indexOf("\"", r1);
                String roundId = eventsSection.substring(r1, r2);
                int p1Start = eventsSection.indexOf("\"participant\"", roundStart);
                int p1 = eventsSection.indexOf("\"", p1Start + 14) + 1;
                int p2 = eventsSection.indexOf("\"", p1);
                String participant = eventsSection.substring(p1, p2);
                int sStart = eventsSection.indexOf("\"score\"", roundStart);
                int sColon = eventsSection.indexOf(":", sStart) + 1;
                int sEnd = eventsSection.indexOf("}", sColon);
                String scoreStr = eventsSection.substring(sColon, sEnd).trim().replaceAll("[^0-9]", "");
                int score = Integer.parseInt(scoreStr);
                String key = roundId + "|" + participant;
                if (!seen.contains(key)) {
                    seen.add(key);
                    scores.put(participant, scores.getOrDefault(participant, 0) + score);
                    System.out.println("Added: " + participant + " round=" + roundId + " score=" + score);
                } else {
                    System.out.println("Duplicate skipped: " + key);
                }
                index = sEnd + 1;
            }
            if (poll < 9) {
                System.out.println("Waiting 5 seconds...");
                Thread.sleep(5000);
            }
        }

        if (scores.isEmpty()) {
            System.out.println("No scores collected. Server may be down. Please try again later.");
            return;
        }

        List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scores.entrySet());
        leaderboard.sort((a, b) -> b.getValue() - a.getValue());
        int totalScore = 0;
        StringBuilder leaderboardJson = new StringBuilder("[");
        for (int i = 0; i < leaderboard.size(); i++) {
            Map.Entry<String, Integer> entry = leaderboard.get(i);
            totalScore += entry.getValue();
            leaderboardJson.append("{\"participant\":\"").append(entry.getKey()).append("\",\"totalScore\":").append(entry.getValue()).append("}");
            if (i < leaderboard.size() - 1) leaderboardJson.append(",");
        }
        leaderboardJson.append("]");
        System.out.println("\n=== FINAL LEADERBOARD ===");
        System.out.println(leaderboardJson);
        System.out.println("Total Score: " + totalScore);
        String submitBody = "{\"regNo\":\"" + REG_NO + "\",\"leaderboard\":" + leaderboardJson + "}";
        HttpRequest submitRequest = HttpRequest.newBuilder()
            .uri(URI.create(BASE_URL + "/quiz/submit"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(submitBody))
            .build();
        HttpResponse<String> submitResponse = client.send(submitRequest, HttpResponse.BodyHandlers.ofString());
        System.out.println("\n=== SUBMIT RESPONSE ===");
        System.out.println(submitResponse.body());
    }
}
