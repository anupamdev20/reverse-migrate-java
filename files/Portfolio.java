public class SwitchPatternsTest {

    record Point(int x, int y) {}
    record Box<T>(T content) {}

    
    sealed interface Shape permits Circle, Square {}
    final class Circle implements Shape {
        double radius;
        Circle(double radius) { this.radius = radius; }
    }
    final class Square implements Shape {
        double side;
        Square(double side) { this.side = side; }
    }

    public static String getTypeDescription(Object obj) {
        return switch (obj) {
            case String s -> "String with length: " + s.length();
            case Integer i -> "Integer with value: " + i;
            case Double d -> "Double with value: " + d;
            default -> "Unknown type";
        };
    }

    public static void testTypePatterns() {
        System.out.println("--- Testing Type Patterns ---");
        System.out.println("Test 1 (String): " + getTypeDescription("Hello")); // Output: String with length: 5
        System.out.println("Test 2 (Integer): " + getTypeDescription(42));     // Output: Integer with value: 42
        System.out.println("Test 3 (Double): " + getTypeDescription(3.14));   // Output: Double with value: 3.14
        System.out.println("Test 4 (Object): " + getTypeDescription(new Object())); // Output: Unknown type
    }
    
    public static String getNumberStatus(Number num) {
        return switch (num) {
            case Integer i when i > 0 -> "Positive integer";
            case Integer i when i < 0 -> "Negative integer";
            case Integer i -> "Zero integer";
            case Double d when d > 0 -> "Positive double";
            case Double d when d < 0 -> "Negative double";
            case Double d -> "Zero double";
            default -> "Other number type";
        };
    }

    public static void testGuardedPatterns() {
        System.out.println("\n--- Testing Guarded Patterns ---");
        System.out.println("Test 1 (Positive Int): " + getNumberStatus(10)); // Output: Positive integer
        System.out.println("Test 2 (Negative Int): " + getNumberStatus(-5)); // Output: Negative integer
        System.out.println("Test 3 (Zero Double): " + getNumberStatus(0.0)); // Output: Zero double
    }


    public static String handleNull(Object obj) {
        return switch (obj) {
            case null -> "It's null!";
            case String s -> "It's a string: " + s;
            default -> "Something else";
        };
    }

    public static void testNullHandling() {
        System.out.println("\n--- Testing Null Handling ---");
        System.out.println("Test 1 (Null): " + handleNull(null));      // Output: It's null!
        System.out.println("Test 2 (String): " + handleNull("data")); // Output: It's a string: data
    }

     public static String processCoordinate(Object obj) {
        return switch (obj) {
            case Point(int x, int y) when x == y -> "Point on diagonal: (" + x + ", " + y + ")";
            case Point(int x, int y) -> "Point off diagonal: (" + x + ", " + y + ")";
            case Box(Point p) -> "Box containing a Point: " + p;
            case Box(var content) -> "Box containing other content: " + content;
            default -> "Not a Point or Box";
        };
    }

    public static void testRecordPatterns() {
        System.out.println("\n--- Testing Record Patterns ---");
        System.out.println("Test 1 (Diagonal Point): " + processCoordinate(new Point(5, 5)));
        System.out.println("Test 2 (Off-Diagonal Point): " + processCoordinate(new Point(2, 7)));
        System.out.println("Test 3 (Nested Record): " + processCoordinate(new Box<>(new Point(1, 1))));
        System.out.println("Test 4 (Other Box Content): " + processCoordinate(new Box<>("Hello")));
    }

     public static String describeShape(Shape shape) {
        // The compiler guarantees exhaustiveness here; no default needed
        return switch (shape) {
            case Circle c -> "A circle with radius " + c.radius;
            case Square s -> "A square with side " + s.side;
        };
    }

    public static void testSealedTypes() {
        System.out.println("\n--- Testing Sealed Types Exhaustiveness ---");
        System.out.println("Test 1 (Circle): " + describeShape(new Circle(10.0)));
        System.out.println("Test 2 (Square): " + describeShape(new Square(4.0)));
    }

}