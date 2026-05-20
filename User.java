// Java 21 Record: Yeh username aur password store karega
public record User(String username, String password) {
    
    // 1. Object ko string mein convert karna taake file mein save ho sake
    public String toFileString() {
        return username + "," + password;
    }

    // 2. File se line read kar ke wapas User object banana
    public static User fromFileString(String line) {
        // Agar line khali hai to kuch mat karo
        if (line == null || line.trim().isEmpty()) {
            return null;
        }
        
        // Comma (,) se username aur password ko alag karna
        String[] parts = line.split(",");
        
        // Make sure karna ke exact 2 hisse hon (username aur password)
        if (parts.length == 2) {
            return new User(parts[0].trim(), parts[1].trim());
        }
        
        return null;
    }
}