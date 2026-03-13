public class SwitchPatternsTest {

    final class Point {

        private final int x;

        private final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        @Override()
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Point that = (Point) o;
            return x == that.x && y == that.y;
        }

        @Override()
        public int hashCode() {
            return java.util.Objects.hash(x, y);
        }

        @Override()
        public String toString() {
            return "Point[" + "x=" + x + ", " + "y=" + y + "]";
        }
    }

    final class Box<T> {

        private final T content;

        public Box(T content) {
            this.content = content;
        }

        public T content() {
            return content;
        }

        @Override()
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Box that = (Box) o;
            return java.util.Objects.equals(content, that.content);
        }

        @Override()
        public int hashCode() {
            return java.util.Objects.hash(content);
        }

        @Override()
        public String toString() {
            return "Box[" + "content=" + content + "]";
        }
    }

    interface Shape {
    }

    final class Circle implements Shape {

        double radius;

        Circle(double radius) {
            this.radius = radius;
        }
    }

    final class Square implements Shape {

        double side;

        Square(double side) {
            this.side = side;
        }
    }

    public static String getTypeDescription(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;
            return "String with length: " + s.length();
        } else if (obj instanceof Integer) {
            Integer i = (Integer) obj;
            return "Integer with value: " + i;
        } else if (obj instanceof Double) {
            Double d = (Double) obj;
            return "Double with value: " + d;
        } else {
            return "Unknown type";
        }
    }

    public static void testTypePatterns() {
        System.out.println("--- Testing Type Patterns ---");
        // Output: String with length: 5
        System.out.println("Test 1 (String): " + getTypeDescription("Hello"));
        // Output: Integer with value: 42
        System.out.println("Test 2 (Integer): " + getTypeDescription(42));
        // Output: Double with value: 3.14
        System.out.println("Test 3 (Double): " + getTypeDescription(3.14));
        // Output: Unknown type
        System.out.println("Test 4 (Object): " + getTypeDescription(new Object()));
    }

    public static String getNumberStatus(Number num) {
        if (num instanceof Integer && ((Integer) num) > 0) {
            Integer i = (Integer) num;
            return "Positive integer";
        } else if (num instanceof Integer && ((Integer) num) < 0) {
            Integer i = (Integer) num;
            return "Negative integer";
        } else if (num instanceof Integer) {
            Integer i = (Integer) num;
            return "Zero integer";
        } else if (num instanceof Double && ((Double) num) > 0) {
            Double d = (Double) num;
            return "Positive double";
        } else if (num instanceof Double && ((Double) num) < 0) {
            Double d = (Double) num;
            return "Negative double";
        } else if (num instanceof Double) {
            Double d = (Double) num;
            return "Zero double";
        } else {
            return "Other number type";
        }
    }

    public static void testGuardedPatterns() {
        System.out.println("\n--- Testing Guarded Patterns ---");
        // Output: Positive integer
        System.out.println("Test 1 (Positive Int): " + getNumberStatus(10));
        // Output: Negative integer
        System.out.println("Test 2 (Negative Int): " + getNumberStatus(-5));
        // Output: Zero double
        System.out.println("Test 3 (Zero Double): " + getNumberStatus(0.0));
    }

    public static String handleNull(Object obj) {
        if (obj instanceof String) {
            String s = (String) obj;
            return "It's a string: " + s;
        } else {
            return "Something else";
        }
    }

    public static void testNullHandling() {
        System.out.println("\n--- Testing Null Handling ---");
        // Output: It's null!
        System.out.println("Test 1 (Null): " + handleNull(null));
        // Output: It's a string: data
        System.out.println("Test 2 (String): " + handleNull("data"));
    }

    public static String processCoordinate(Object obj) {
        return switch(obj) {
            case Point(int x, int y) when x == y ->
                "Point on diagonal: (" + x + ", " + y + ")";
            case Point(int x, int y) ->
                "Point off diagonal: (" + x + ", " + y + ")";
            case Box(Point p) ->
                "Box containing a Point: " + p;
            case Box(var content) ->
                "Box containing other content: " + content;
            default ->
                "Not a Point or Box";
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
        if (shape instanceof Circle) {
            Circle c = (Circle) shape;
            return "A circle with radius " + c.radius;
        } else if (shape instanceof Square) {
            Square s = (Square) shape;
            return "A square with side " + s.side;
        }
    }

    public static void testSealedTypes() {
        System.out.println("\n--- Testing Sealed Types Exhaustiveness ---");
        System.out.println("Test 1 (Circle): " + describeShape(new Circle(10.0)));
        System.out.println("Test 2 (Square): " + describeShape(new Square(4.0)));
    }
}
