public class TextManager {
    private final String input;
    private int position;

    public TextManager(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        this.input = input;
        this.position = 0;
    }


    public boolean isAtEnd() {
        return position >= input.length();
    }


    public char PeekCharacter() {
        if (isAtEnd()) {
            throw new IndexOutOfBoundsException("Attempted to peek past end of input at position " + position);
        }
        return input.charAt(position);
    }


    public char PeekCharacter(int dist) {
        int idx = position + dist;
        if (idx >= input.length()) {
            throw new IndexOutOfBoundsException("Attempted to peek past end of input at index " + idx);
        }
        return input.charAt(idx);
    }


    public char GetCharacter() {
        if (isAtEnd()) {
            throw new IndexOutOfBoundsException("Attempted to get character past end of input at position " + position);
        }
        return input.charAt(position++);
    }
}
