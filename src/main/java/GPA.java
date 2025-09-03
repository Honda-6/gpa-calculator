import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

record CourseData(double points, int numOfHours) {
    @Override
    public String toString() {
        return "GPA=%.2f, hours=%d".formatted(points, numOfHours);
    }
}

public class GPA {
    private static final String API_URL = "http://193.227.14.58/api/student-courses?size=150&studentId.equals={YOUR ID}&includeWithdraw.equals=true";
    private static final String BEARER_TOKEN = "{YOUR TOKEN}";

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        var gpaCalculator = new GPA();
        try {
            var courses = gpaCalculator.fetchAndProcessCourses();

            System.out.println("Fetched %d courses:".formatted(courses.size()));
            System.out.println("========================================");
            courses.forEach(System.out::println);

            var statistics = calculateStatistics(courses);

            System.out.println("""
                Total Points: %.2f
                Total Hours: %d
                GPA: %.2f
                """.formatted(statistics.totalPoints(), statistics.totalHours(), statistics.gpa()));

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    record Statistics(double totalPoints, int totalHours, double gpa) {}

    private static Statistics calculateStatistics(List<CourseData> courses) {
        var totalPoints = courses.stream()
                .filter(course -> course.points() != 0)
                .mapToDouble(course -> course.points() * course.numOfHours())
                .sum();

        var totalHours = courses.stream()
                .filter(course -> course.points() != 0)
                .mapToInt(CourseData::numOfHours)
                .sum();

        var gpa = totalPoints / totalHours;
        return new Statistics(totalPoints, totalHours, gpa);
    }

    public List<CourseData> fetchAndProcessCourses() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + BEARER_TOKEN)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        return switch (response.statusCode()) {
            case 200 -> processCourseData(response.body());
            default -> throw new RuntimeException(
                "HTTP request failed with status code: %d, body: %s"
                    .formatted(response.statusCode(), response.body())
            );
        };
    }

    private List<CourseData> processCourseData(String jsonResponse) throws IOException {
        var rootNode = OBJECT_MAPPER.readTree(jsonResponse);
        if (!rootNode.isArray()) {
            throw new IllegalArgumentException("Expected JSON array but got: " + rootNode.getNodeType());
        }
        return java.util.stream.StreamSupport.stream(rootNode.spliterator(), false)
                .map(this::extractCourseInfo)
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<CourseData> extractCourseInfo(JsonNode courseNode) {
        try {
            var points = courseNode.has("points") ? courseNode.get("points").asDouble() : 0.0;
            var numOfHours = Optional.ofNullable(courseNode.get("course"))
                    .filter(JsonNode::isObject)
                    .map(courseObject -> courseObject.get("numOfHours"))
                    .map(JsonNode::asInt)
                    .orElse(0);
            return Optional.of(new CourseData(points, numOfHours));
        } catch (Exception e) {
            System.err.println("Error extracting course info: " + e.getMessage());
            return Optional.empty();
        }
    }
}
