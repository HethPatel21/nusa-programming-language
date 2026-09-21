import AST.*;
import java.util.*;

public class Lexer {
    private final TextManager textManager;
    private final Map<String, Token.TokenTypes> keywords;
    private final LinkedList<Token> tokens = new LinkedList<>();
    private int currentIndentLevel = 0;

    private int line = 1;
    private int col = 1;

    public Lexer(String input) {
        this.textManager = new TextManager(input);
        currentIndentLevel=(0); //indentation base

        //initialize keywords
        keywords = new HashMap<>();
        keywords.put("var", Token.TokenTypes.VAR);
        keywords.put("unique", Token.TokenTypes.UNIQUE);
    }

    public LinkedList<Token> Lex() throws SyntaxErrorException {
        while (!textManager.isAtEnd()) {
            char c = textManager.PeekCharacter();

            if (c == '\n') {
                handleNewline();
            } else if (c == ' ' || c == '\t') {
                skipSpaces();
            } else if (Character.isLetter(c)) {
                tokens.add(readWord());
            } else if (Character.isDigit(c)) {
                tokens.add(readNumber());
            } else {
                tokens.add(readPunctuation());
            }
        }


        while (currentIndentLevel > 0) {
            currentIndentLevel--;
            tokens.add(new Token(Token.TokenTypes.DEDENT, line, col));
        }
        tokens.add(new Token(Token.TokenTypes.NEWLINE, line, col));

        return tokens;
    }



    private void handleNewline() throws SyntaxErrorException {
        textManager.GetCharacter(); 
        tokens.add(new Token(Token.TokenTypes.NEWLINE, line, col));
        line++;
        col = 1;

        int spaces = 0;
        while (!textManager.isAtEnd()) {
            char c = textManager.PeekCharacter();
            if (c == ' ') {
                spaces++;
                textManager.GetCharacter();
                col++;
            } else if (c == '\t') {
                spaces += 4;
                textManager.GetCharacter();
                col += 4;
            } else {
                break;
            }
        }

        if (spaces % 4 != 0) {
            throw new SyntaxErrorException("Indentation must be multiple of 4", line, col);
        }

        int newLevel = spaces / 4;

        if (newLevel > currentIndentLevel) {
            int indentsToAdd = newLevel - currentIndentLevel;
            for (int i = 0; i < indentsToAdd; i++) {
                tokens.add(new Token(Token.TokenTypes.INDENT, line, col));
            }
        } else if (newLevel < currentIndentLevel) {
            int dedents = currentIndentLevel - newLevel;
            for (int i = 0; i < dedents; i++) {
                tokens.add(new Token(Token.TokenTypes.DEDENT, line, col));
            }
        }


        currentIndentLevel = newLevel;
    }



    private void skipSpaces() {
        while (!textManager.isAtEnd()) {
            char c = textManager.PeekCharacter();
            if (c == ' ') {
                textManager.GetCharacter();
                col++;
            } else if (c == '\t') {
                textManager.GetCharacter();
                col += 4;
            } else {
                return;
            }
        }
    }

    private Token readWord() {
        int startCol = col;
        StringBuilder sb = new StringBuilder();

        while (!textManager.isAtEnd() && (Character.isLetterOrDigit(textManager.PeekCharacter()) || textManager.PeekCharacter() == '_')) {
            sb.append(textManager.GetCharacter());
            col++;
        }

        String word = sb.toString();
        Token.TokenTypes type = keywords.getOrDefault(word, Token.TokenTypes.IDENTIFIER);
        return new Token(type, line, startCol, word);
    }

    private Token readNumber() {
        int startCol = col;
        StringBuilder sb = new StringBuilder();

        while (!textManager.isAtEnd() && Character.isDigit(textManager.PeekCharacter())) {
            sb.append(textManager.GetCharacter());
            col++;
        }

        return new Token(Token.TokenTypes.NUMBER, line, startCol, sb.toString());
    }

    private Token readPunctuation() throws SyntaxErrorException {
        int startCol = col;
        char first = textManager.GetCharacter();
        col++;
        if (first == '!') {
            if (!textManager.isAtEnd() && textManager.PeekCharacter() == '=') {
                textManager.GetCharacter();
                col++;
                return new Token(Token.TokenTypes.NOTEQUAL, line, startCol, "!=");
            } else {
                throw new SyntaxErrorException("Unexpected character: " + first, line, startCol);
            }
        } else if (first == '=') {
            if (!textManager.isAtEnd() && textManager.PeekCharacter() == '>') {
                textManager.GetCharacter();
                col++;
                return new Token(Token.TokenTypes.YIELDS, line, startCol, "=>");
            } else {
                return new Token(Token.TokenTypes.EQUAL, line, startCol, "=");
            }
        }

        switch (first) {
            case '=': return new Token(Token.TokenTypes.EQUAL, line, startCol, "=");
            case '{': return new Token(Token.TokenTypes.LEFTCURLY, line, startCol, "{");
            case '}': return new Token(Token.TokenTypes.RIGHTCURLY, line, startCol, "}");
            case '[':return new Token(Token.TokenTypes.LEFTBRACE, line, startCol, "[");
            case ']':return new Token(Token.TokenTypes.RIGHTBRACE, line, startCol, "]");
            case ',': return new Token(Token.TokenTypes.COMMA, line, startCol, ",");
            case ':': return new Token(Token.TokenTypes.COLON, line, startCol, ":");
            case '.': return new Token(Token.TokenTypes.DOT, line, startCol, ".");
            default:
                throw new SyntaxErrorException("Unexpected character: " + first, line, startCol);
        }
    }
}
