import java.io.IOException;
import java.util.ArrayList;
import java.util.List;



import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class CourseData {
        private double points;
        private int numOfHours;
        
        public CourseData(double points, int numOfHours) {
            this.points = points;
            this.numOfHours = numOfHours;
        }
        
        public double getPoints() { 
            return points; 
        }
        
        public int getNumOfHours() { 
            return numOfHours; 
        }
        
        @Override
        public String toString() {
            return String.format("GPA=%.2f, hours=%d", points, numOfHours);
        }
}

public class GPA {
    
    private static final String API_URL = "http://193.227.14.58/api/student-courses?size=150&studentId.equals={YOUR ID}&includeWithdraw.equals=true";
    private static final String BEARER_TOKEN = "{YOUR TOKEN}";
    
    public static void main(String[] args) {
        GPA gpaCalculator = new GPA();
        try {
            List<CourseData> courses = gpaCalculator.fetchAndProcessCourses();
            

            System.out.println("Fetched " + courses.size() + " courses:");
            System.out.println("========================================");
            for (CourseData course : courses) {
                System.out.println(course);
            }
            
            double finalTotalPoints = 0;
            int totalHours = 0;
            for (CourseData course : courses) {
                if(course.getPoints() != 0) {
                    finalTotalPoints += course.getPoints() * course.getNumOfHours();
                    totalHours += course.getNumOfHours();
                }
            }
            
            System.out.println("\nTotal Points: " + finalTotalPoints);
            System.out.println("Total Hours: " + totalHours);
            if (totalHours > 0) {
                System.out.println("GPA: " + String.format("%.2f", finalTotalPoints / totalHours));
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public List<CourseData> fetchAndProcessCourses() throws IOException {
        List<CourseData> courses = new ArrayList<>();
        
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            
            HttpGet httpGet = new HttpGet(API_URL);
            
            
            httpGet.setHeader("Authorization", "Bearer " + BEARER_TOKEN);
            httpGet.setHeader("Content-Type", "application/json");
            httpGet.setHeader("Accept", "application/json");
            
            
            HttpResponse response = httpClient.execute(httpGet);
            HttpEntity entity = response.getEntity();
            
            if (response.getStatusLine().getStatusCode() == 200) {
                if (entity != null) {
                    String jsonResponse = EntityUtils.toString(entity);
                    courses = processCourseData(jsonResponse);
                }
            } else {
                String errorBody = entity != null ? EntityUtils.toString(entity) : "No response body";
                throw new RuntimeException("HTTP request failed with status code: " + 
                        response.getStatusLine().getStatusCode() + ", body: " + errorBody);
            }
        }
        
        return courses;
    }
    
    private List<CourseData> processCourseData(String jsonResponse) throws IOException {
        List<CourseData> courses = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(jsonResponse);
        
        if (rootNode.isArray()) {
            for (JsonNode courseNode : rootNode) {
                CourseData courseData = extractCourseInfo(courseNode);
                if (courseData != null) {
                    courses.add(courseData);
                }
            }
        } else {
            throw new IllegalArgumentException("Expected JSON array but got: " + rootNode.getNodeType());
        }
        
        return courses;
    }
    
    private CourseData extractCourseInfo(JsonNode courseNode) {
        try {
            
            double points = courseNode.has("points") ? courseNode.get("points").asDouble() : 0.0;
            
            int numOfHours = 0;
            if (courseNode.has("course") && courseNode.get("course").isObject()) {
                JsonNode courseObject = courseNode.get("course");
                numOfHours = courseObject.has("numOfHours") ? 
                        courseObject.get("numOfHours").asInt() : 0;
            }
            
            return new CourseData(points, numOfHours);
            
        } catch (Exception e) {
            System.err.println("Error extracting course info: " + e.getMessage());
            return null;
        }
    }
    
}
