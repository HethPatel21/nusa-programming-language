import AST.Token;
import java.util.LinkedList;
import java.util.Optional;


public class TokenManager {
    private final LinkedList<Token> tokens;

    public TokenManager(LinkedList<Token> tokens) {
        if (tokens == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        this.tokens = tokens;
    }

    public int getLine() {
        if (tokens.isEmpty()) return -1;
        return tokens.getFirst().LineNumber;
    }

    public int getColumn() {
        if (tokens.isEmpty()) return -1;
        return tokens.getFirst().ColumnNumber;
    }

    public boolean Done() {
        return tokens.isEmpty();
    }

    public Optional<Token> MatchAndRemove(Token.TokenTypes t) {
        if (!tokens.isEmpty() && tokens.getFirst().Type == t) {
            return Optional.of(tokens.removeFirst());
        }
        return Optional.empty();
    }

    public Optional<Token> Peek(int i) {
        if (i < 0 || i >= tokens.size()) return Optional.empty();
        return Optional.of(tokens.get(i));
    }

}
