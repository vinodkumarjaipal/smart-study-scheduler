/**
 * Java 21 Record for Study Session.
 * Implements Comparable taake isko automatically End Time ke hisaab se sort kiya ja sake,
 * Weighted Interval Scheduling algorithm ki pehli requirement ke hisab se.
 */
public record StudySession(
    String subjectName, 
    int startTime, 
    int endTime, 
    int priority
) implements Comparable<StudySession> {

    // Compact Constructor Object banne se pehle data validate karta hai
    public StudySession {
        // Validation: Start time hamesha End time se chota hoga
        if (startTime >= endTime) {
            throw new IllegalArgumentException("Error: Start time must be strictly less than end time.");
        }
        // Validation: Priority negative nahi ho sakti
        if (priority < 0) {
            throw new IllegalArgumentException("Error: Priority cannot be negative.");
        }
    }

    // override method DP algorithm ke liye sessions ko ascending order mein sort karna
    @Override
    public int compareTo(StudySession other) {
        return Integer.compare(this.endTime, other.endTime);
    }
}