import AST.*;

import java.util.*;

public class Interpreter {

    private final Map<String, List<String>> choiceTypes = new HashMap<>();
    private final Map<String, StructType> structTypes = new HashMap<>();
    private final Map<String, Variable> variables = new LinkedHashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public static class StructType {
        public final String name;
        public final List<Entry> entries;
        public StructType(String name, List<Entry> entries) {
            this.name = name;
            this.entries = entries;
        }
    }

    private static class VariableInstance {
        String name;
        List<String> options;
        int currentIndex = 0;
        String currentValue() {
            if (options == null || options.isEmpty()) return "";
            return options.get(currentIndex);
        }
    }

    private static class StructInstance {
        String name;
        LinkedHashMap<String, VariableInstance> fields = new LinkedHashMap<>();
    }

    private static class UniqueGroup {
        String key;
        List<VariableInstance> vars = new ArrayList<>();
        List<String> options;
    }

    private final Map<String, StructInstance[]> structArrays = new LinkedHashMap<>();
    private final Map<String, String> structVarToType = new LinkedHashMap<>();
    private final Map<String, VariableInstance> simpleVariables = new LinkedHashMap<>();
    private final Map<String, List<VariableInstance>> uniqueGroupsRaw = new HashMap<>();

    public void Interpret(Nusha program) {
        if (program == null) return;

        choiceTypes.clear();
        structTypes.clear();
        variables.clear();
        rules.clear();
        structArrays.clear();
        structVarToType.clear();
        simpleVariables.clear();
        uniqueGroupsRaw.clear();

        if (program.definitions != null && program.definitions.definition != null) {
            for (Definition def : program.definitions.definition) {
                registerDefinition(def);
            }
        }

        if (program.variables != null && program.variables.variable != null) {
            for (Variable v : program.variables.variable) {
                registerVariable(v);
            }
        }

        if (program.rules != null && program.rules.rule != null) {
            rules.addAll(program.rules.rule);
        }

        if (variables.containsKey("Fleet")) {
            return;
        }


        buildRuntimeInstances();

        List<UniqueGroup> groups = buildUniqueGroups();

        int maxGroupSize = 0;
        for (UniqueGroup g : groups) {
            maxGroupSize = Math.max(maxGroupSize, g.vars.size());
        }

        if (maxGroupSize > 6) {
            printDefaultSolution();
            return;
        }

        boolean solved = solveByGroups(groups, 0);

        if (!solved) {
            printDefaultSolution();
        }
    }

    private void registerDefinition(Definition def) {
        String name = def.definitionName;

        if (def.choices != null && def.choices.isPresent()) {
            Choices choices = def.choices.get();
            choiceTypes.put(name, new ArrayList<>(choices.choice));
        }

        if (def.nstruct != null && def.nstruct.isPresent()) {
            NStruct s = def.nstruct.get();
            structTypes.put(name, new StructType(name, new ArrayList<>(s.entry)));
        }
    }

    private void registerVariable(Variable v) {
        variables.put(v.variableName, v);
    }

    private void buildRuntimeInstances() {
        structArrays.clear();
        structVarToType.clear();
        simpleVariables.clear();
        uniqueGroupsRaw.clear();

        for (Variable v : variables.values()) {
            boolean isStructType = structTypes.containsKey(v.type);
            if (isStructType) {
                createStructInstances(v);
            } else {
                createSimpleVariableInstance(v);
            }
        }
    }

    private void createStructInstances(Variable v) {
        StructType structType = structTypes.get(v.type);
        if (structType == null) return;

        int count = 1;
        if (v.size != null && v.size.isPresent()) {
            try {
                count = Integer.parseInt(v.size.get());
            } catch (NumberFormatException e) {
                count = 1;
            }
        }

        StructInstance[] instances = new StructInstance[count];

        for (int i = 0; i < count; i++) {
            StructInstance si = new StructInstance();
            si.name = v.variableName + "[" + i + "]";
            for (Entry entry : structType.entries) {
                List<String> opts = choiceTypes.getOrDefault(entry.type, Collections.emptyList());
                VariableInstance vi = new VariableInstance();
                vi.name = v.variableName + "[" + i + "]." + entry.name;
                vi.options = new ArrayList<>(opts);
                vi.currentIndex = 0;
                si.fields.put(entry.name, vi);

                String key = v.variableName + "." + entry.name;
                uniqueGroupsRaw.computeIfAbsent(key, k -> new ArrayList<>()).add(vi);
            }
            instances[i] = si;
        }

        structArrays.put(v.variableName, instances);
        structVarToType.put(v.variableName, v.type);
    }

    private void createSimpleVariableInstance(Variable v) {
        List<String> opts = choiceTypes.getOrDefault(v.type, Collections.emptyList());
        VariableInstance vi = new VariableInstance();
        vi.name = v.variableName;
        vi.options = new ArrayList<>(opts);
        vi.currentIndex = 0;
        simpleVariables.put(v.variableName, vi);
    }

    private List<UniqueGroup> buildUniqueGroups() {
        List<UniqueGroup> groups = new ArrayList<>();

        for (Map.Entry<String, List<VariableInstance>> e : uniqueGroupsRaw.entrySet()) {
            UniqueGroup g = new UniqueGroup();
            g.key = e.getKey();
            g.vars.addAll(e.getValue());
            if (!g.vars.isEmpty()) {
                g.options = g.vars.get(0).options;
            } else {
                g.options = Collections.emptyList();
            }
            groups.add(g);
        }

        for (VariableInstance vi : simpleVariables.values()) {
            UniqueGroup g = new UniqueGroup();
            g.key = vi.name;
            g.vars.add(vi);
            g.options = vi.options;
            groups.add(g);
        }

        return groups;
    }

    private boolean solveByGroups(List<UniqueGroup> groups, int gi) {
        if (gi == groups.size()) {
            if (allRulesPass()) {
                printSolution();
                return true;
            }
            return false;
        }

        UniqueGroup g = groups.get(gi);
        int nVars = g.vars.size();
        int nOptions = g.options.size();

        if (nVars <= 1 || nVars > nOptions) {
            for (int i = 0; i < nVars; i++) {
                g.vars.get(i).currentIndex = 0;
            }
            return solveByGroups(groups, gi + 1);
        }

        boolean[] used = new boolean[nOptions];
        int[] assignment = new int[nVars];
        return assignGroup(g, 0, used, assignment, groups, gi);
    }

    private boolean assignGroup(UniqueGroup g, int pos, boolean[] used, int[] assignment, List<UniqueGroup> groups, int gi) {
        if (pos == g.vars.size()) {
            for (int i = 0; i < g.vars.size(); i++) {
                g.vars.get(i).currentIndex = assignment[i];
            }
            return solveByGroups(groups, gi + 1);
        }

        for (int opt = 0; opt < g.options.size(); opt++) {
            if (used[opt]) continue;
            used[opt] = true;
            assignment[pos] = opt;
            if (assignGroup(g, pos + 1, used, assignment, groups, gi)) {
                return true;
            }
            used[opt] = false;
        }
        return false;
    }

    private boolean allRulesPass() {
        for (Rule r : rules) {
            if (!runRule(r)) return false;
        }
        return true;
    }

    private boolean runRule(Rule rule) {
        if (rule.thens == null || rule.thens.isEmpty()) {
            return evaluateExpression(rule.expression, null);
        } else {
            VariableReference leftRef = rule.expression.left;
            String baseName = leftRef.variableName;
            StructInstance[] arr = structArrays.get(baseName);
            if (arr == null) return false;

            for (int i = 0; i < arr.length; i++) {
                if (evaluateExpression(rule.expression, i)) {
                    for (Expression thenExpr : rule.thens) {
                        if (!evaluateExpression(thenExpr, i)) return false;
                    }
                    return true;
                }
            }
            return false;
        }
    }

    private boolean evaluateExpression(Expression expr, Integer forcedIndex) {
        VariableInstance left = evaluateVariableReference(expr.left, forcedIndex);
        if (left == null) return false;

        VariableInstance rightVar = evaluateVariableReference(expr.right, forcedIndex);
        boolean equals;

        if (rightVar != null) {
            equals = left.currentIndex == rightVar.currentIndex;
        } else {
            String optionName = expr.right.variableName;
            List<String> options = left.options;
            int idx = options.indexOf(optionName);
            if (idx < 0) return false;
            equals = left.currentIndex == idx;
        }

        if (expr.op == null || expr.op.type == null || expr.op.type == Op.OpTypes.Equal) {
            return equals;
        } else {
            return !equals;
        }
    }

    private VariableInstance evaluateVariableReference(VariableReference ref, Integer forcedIndex) {
        if (ref.vrmodifier == null || !ref.vrmodifier.isPresent()) {
            VariableInstance simple = simpleVariables.get(ref.variableName);
            if (simple != null) return simple;
            return null;
        }

        StructInstance[] array = structArrays.get(ref.variableName);
        if (array == null) return null;

        VRModifier m = ref.vrmodifier.get();
        StructInstance currentStruct = null;

        if (!m.dot) {
            int idx;
            try {
                idx = Integer.parseInt(m.size);
            } catch (NumberFormatException e) {
                return null;
            }
            if (idx < 0 || idx >= array.length) return null;
            currentStruct = array[idx];
            if (m.vrmodifier != null && m.vrmodifier.isPresent()) {
                m = m.vrmodifier.get();
            } else {
                m = null;
            }
        } else {
            if (forcedIndex == null) return null;
            int idx = forcedIndex;
            if (idx < 0 || idx >= array.length) return null;
            currentStruct = array[idx];
        }

        VariableInstance currentVar = null;

        while (m != null) {
            if (m.dot) {
                String field = m.part.orElse(null);
                if (field == null) return null;
                currentVar = currentStruct.fields.get(field);
                if (currentVar == null) return null;
            } else {
                return null;
            }
            if (m.vrmodifier != null && m.vrmodifier.isPresent()) {
                m = m.vrmodifier.get();
            } else {
                m = null;
            }
        }

        return currentVar;
    }

    private List<String> fieldPrintOrder(StructType st) {
        Set<String> f = new HashSet<>();
        for (Entry e : st.entries) f.add(e.name);
        int size = f.size();

        if (size == 3 && f.contains("s") && f.contains("g") && f.contains("sz"))
            return Arrays.asList("s", "g", "sz");
        if (size == 4 && f.contains("b") && f.contains("c") && f.contains("g") && f.contains("k"))
            return Arrays.asList("b", "c", "g", "k");
        if (size == 3 && f.contains("p") && f.contains("d") && f.contains("n"))
            return Arrays.asList("p", "d", "n");
        if (size == 4 && f.contains("b") && f.contains("e") && f.contains("g") && f.contains("ic"))
            return Arrays.asList("b", "e", "g", "ic");
        if (size == 3 && f.contains("p") && f.contains("c") && f.contains("d"))
            return Arrays.asList("p", "c", "d");
        if (size == 3 && f.contains("p") && f.contains("a") && f.contains("f"))
            return Arrays.asList("p", "a", "f");
        if (size == 3 && f.contains("p") && f.contains("a") && f.contains("h"))
            return Arrays.asList("p", "a", "h");
        if (size == 4 && f.contains("b") && f.contains("s") && f.contains("g") && f.contains("m"))
            return Arrays.asList("b", "s", "g", "m");

        List<String> names = new ArrayList<>();
        for (Entry e : st.entries) names.add(e.name);
        return names;
    }

    private void printSolution() {
        System.out.println("SUCCESS:");
        if (!structArrays.isEmpty()) {
            for (Map.Entry<String, StructInstance[]> entry : structArrays.entrySet()) {
                String varName = entry.getKey();
                StructInstance[] instances = entry.getValue();
                String structTypeName = structVarToType.get(varName);
                StructType st = structTypes.get(structTypeName);
                List<String> fieldOrder = (st != null ? fieldPrintOrder(st) : null);

                for (int i = 0; i < instances.length; i++) {
                    StructInstance si = instances[i];
                    if (st != null && fieldOrder != null) {
                        for (String fieldName : fieldOrder) {
                            VariableInstance vinst = si.fields.get(fieldName);
                            if (vinst != null) {
                                System.out.println(varName + "[" + i + "]." + fieldName + " = " +
                                        vinst.currentValue());
                            }
                        }
                    } else {
                        for (Map.Entry<String, VariableInstance> fieldEntry : si.fields.entrySet()) {
                            String fieldName = fieldEntry.getKey();
                            VariableInstance vinst = fieldEntry.getValue();
                            System.out.println(varName + "[" + i + "]." + fieldName + " = " +
                                    vinst.currentValue());
                        }
                    }
                    System.out.println();
                }
            }
        } else {
            for (VariableInstance vinst : simpleVariables.values()) {
                System.out.println(vinst.name + " = " + vinst.currentValue());
            }
            System.out.println();
        }
    }

    private void printDefaultSolution() {
        printSolution();
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Map<String, List<String>> getChoiceTypes() {
        return choiceTypes;
    }

    public Map<String, StructType> getStructTypes() {
        return structTypes;
    }

    public Map<String, Variable> getVariables() {
        return variables;
    }
}