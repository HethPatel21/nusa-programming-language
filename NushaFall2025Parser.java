import AST.*;
import java.util.Optional;
import java.util.LinkedList;

public class NushaFall2025Parser {
    private TokenManager tm;


    public Optional<Nusha> Nusha(LinkedList<Token> tokens) throws SyntaxErrorException {
        if (tokens == null) throw new IllegalArgumentException("Input cannot be null");
        this.tm = new TokenManager(tokens);

        Nusha root = new Nusha();
        root.definitions = Definitions();
        root.variables = Variables();
        root.rules = Rules();

        return Optional.of(root);
    }


    private Definitions Definitions() throws SyntaxErrorException {
        Definitions defs = new Definitions();
        while (tm.Peek(0).isPresent() &&
                tm.Peek(0).get().Type == Token.TokenTypes.IDENTIFIER) {
            defs.definition.add(Definition());
        }
        return defs;
    }

    private Definition Definition() throws SyntaxErrorException {
        Token nameTok = Require(Token.TokenTypes.IDENTIFIER);
        Require(Token.TokenTypes.EQUAL);

        Definition def = new Definition();
        def.definitionName = nameTok.Value.orElse("");

        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.LEFTCURLY).orElse(false)) {
            def.choices = Optional.of(Choices());
            def.nstruct = Optional.empty();
        } else if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.LEFTBRACE).orElse(false)) {
            def.nstruct = Optional.of(NStruct());
            def.choices = Optional.empty();
        } else {
            throw new SyntaxErrorException("Expected '{' or '['", tm.getLine(), tm.getColumn());
        }

        SkipNewlines();
        return def;
    }

    private Choices Choices() throws SyntaxErrorException {
        Require(Token.TokenTypes.LEFTCURLY);
        Choices c = new Choices();

        Token first = Require(Token.TokenTypes.IDENTIFIER);
        c.choice.add(first.Value.orElse(""));

        while (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.COMMA).orElse(false)) {
            Require(Token.TokenTypes.COMMA);
            Token next = Require(Token.TokenTypes.IDENTIFIER);
            c.choice.add(next.Value.orElse(""));
        }

        Require(Token.TokenTypes.RIGHTCURLY);
        return c;
    }

    private NStruct NStruct() throws SyntaxErrorException {
        Require(Token.TokenTypes.LEFTBRACE);
        NStruct s = new NStruct();

        s.entry.add(Entry());
        while (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.COMMA).orElse(false)) {
            Require(Token.TokenTypes.COMMA);
            s.entry.add(Entry());
        }

        Require(Token.TokenTypes.RIGHTBRACE);
        return s;
    }

    private Entry Entry() throws SyntaxErrorException {
        Entry e = new Entry();
        e.unique = false;

        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.UNIQUE).orElse(false)) {
            Require(Token.TokenTypes.UNIQUE);
            e.unique = true;
        }

        Token typeTok = Require(Token.TokenTypes.IDENTIFIER);
        Token nameTok = Require(Token.TokenTypes.IDENTIFIER);

        e.type = typeTok.Value.orElse("");
        e.name = nameTok.Value.orElse("");
        return e;
    }


    private Variables Variables() throws SyntaxErrorException {
        Variables vars = new Variables();
        while (tm.Peek(0).isPresent() &&
                tm.Peek(0).get().Type == Token.TokenTypes.VAR) {
            vars.variable.add(Variable());
        }
        return vars;
    }

    private Variable Variable() throws SyntaxErrorException {
        Require(Token.TokenTypes.VAR);
        Token nameTok = Require(Token.TokenTypes.IDENTIFIER);
        String varName = nameTok.Value.orElse("");

        Require(Token.TokenTypes.COLON);
        Token typeTok = Require(Token.TokenTypes.IDENTIFIER);
        String typeName = typeTok.Value.orElse("");

        Optional<String> size = Optional.empty();
        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.LEFTBRACE).orElse(false)) {
            Require(Token.TokenTypes.LEFTBRACE);
            Token numTok = Require(Token.TokenTypes.NUMBER);
            size = numTok.Value;
            Require(Token.TokenTypes.RIGHTBRACE);
        }

        SkipNewlines();

        Variable v = new Variable();
        v.variableName = varName;
        v.type = typeName;
        v.size = size;
        return v;
    }


    private Rules Rules() throws SyntaxErrorException {
        Rules rs = new Rules();
        SkipNewlines();
        while (tm.Peek(0).isPresent() &&
                tm.Peek(0).get().Type == Token.TokenTypes.IDENTIFIER) {
            rs.rule.add(Rule());
            SkipNewlines();
        }
        return rs;
    }

    private Rule Rule() throws SyntaxErrorException {
        Rule r = new Rule();
        Expression expr = Expression();
        r.expression = expr;


        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.YIELDS).orElse(false)) {
            Require(Token.TokenTypes.YIELDS);
            SkipNewlines();
            Require(Token.TokenTypes.INDENT);
            SkipNewlines();

            while (tm.Peek(0).isPresent() &&
                    tm.Peek(0).get().Type != Token.TokenTypes.DEDENT) {
                r.thens.add(Expression());
                SkipNewlines();
            }
            Require(Token.TokenTypes.DEDENT);
        }

        return r;
    }

    private Expression Expression() throws SyntaxErrorException {
        Expression e = new Expression();
        e.left = VariableReference();
        e.op = Op();
        e.right = VariableReference();
        return e;
    }

    private Op Op() throws SyntaxErrorException {
        Optional<Token> cur = tm.Peek(0);
        if (cur.isEmpty()) {
            throw new SyntaxErrorException("Expected '=' or '!='", tm.getLine(), tm.getColumn());
        }

        Token t = cur.get();
        Op o = new Op();

        if (t.Type == Token.TokenTypes.EQUAL) {
            tm.MatchAndRemove(Token.TokenTypes.EQUAL);
            o.type = Op.OpTypes.Equal;
            return o;
        } else if (t.Type == Token.TokenTypes.YIELDS) {
            tm.MatchAndRemove(Token.TokenTypes.YIELDS);
            o.type = Op.OpTypes.Equal;
            return o;
        } else if (t.Type == Token.TokenTypes.NOTEQUAL) {
            tm.MatchAndRemove(Token.TokenTypes.NOTEQUAL);
            o.type = Op.OpTypes.NotEqual;
            return o;
        }

        throw new SyntaxErrorException("Expected '=' or '!=' but found " + t.Type,
                tm.getLine(), tm.getColumn());
    }

    private VariableReference VariableReference() throws SyntaxErrorException {
        Token idTok = Require(Token.TokenTypes.IDENTIFIER);
        VariableReference vr = new VariableReference();
        vr.variableName = idTok.Value.orElse("");

        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.DOT ||
                t.Type == Token.TokenTypes.LEFTBRACE).orElse(false)) {
            vr.vrmodifier = Optional.of(VRModifier());
        } else {
            vr.vrmodifier = Optional.empty();
        }

        return vr;
    }

    private VRModifier VRModifier() throws SyntaxErrorException {
        VRModifier m = new VRModifier();

        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.DOT).orElse(false)) {
            Require(Token.TokenTypes.DOT);
            Token idTok = Require(Token.TokenTypes.IDENTIFIER);
            m.dot = true;
            m.part = Optional.of(idTok.Value.orElse(""));
            m.size = "";
            if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.DOT ||
                    t.Type == Token.TokenTypes.LEFTBRACE).orElse(false)) {
                m.vrmodifier = Optional.of(VRModifier());
            } else {
                m.vrmodifier = Optional.empty();
            }
            return m;
        }

        if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.LEFTBRACE).orElse(false)) {
            Require(Token.TokenTypes.LEFTBRACE);
            Token numTok = Require(Token.TokenTypes.NUMBER);
            m.dot = false;
            m.part = Optional.empty();
            m.size = numTok.Value.orElse("");
            Require(Token.TokenTypes.RIGHTBRACE);
            if (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.DOT ||
                    t.Type == Token.TokenTypes.LEFTBRACE).orElse(false)) {
                m.vrmodifier = Optional.of(VRModifier());
            } else {
                m.vrmodifier = Optional.empty();
            }
            return m;
        }

        throw new SyntaxErrorException("Expected VRModifier ('.' or '[')", tm.getLine(), tm.getColumn());
    }


    private Token Require(Token.TokenTypes expected) throws SyntaxErrorException {
        Optional<Token> maybe = tm.MatchAndRemove(expected);
        if (maybe.isPresent()) return maybe.get();
        String found = tm.Peek(0).map(t -> t.Type + " " + t.Value.orElse("")).orElse("EOF");
        throw new SyntaxErrorException("Expected " + expected + " but found " + found,
                tm.getLine(), tm.getColumn());
    }

    private void SkipNewlines() {
        while (tm.Peek(0).map(t -> t.Type == Token.TokenTypes.NEWLINE).orElse(false)) {
            tm.MatchAndRemove(Token.TokenTypes.NEWLINE);
        }
    }
}
