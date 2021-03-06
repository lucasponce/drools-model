package org.drools.model.impl;

import org.drools.model.AccumulateFunction;
import org.drools.model.Condition;
import org.drools.model.ExistentialPattern;
import org.drools.model.Pattern;
import org.drools.model.Variable;
import org.drools.model.flow.AccumulateExprViewItem;
import org.drools.model.flow.CombinedExprViewItem;
import org.drools.model.flow.Expr1ViewItem;
import org.drools.model.flow.Expr2ViewItem;
import org.drools.model.flow.ExprViewItem;
import org.drools.model.flow.InputViewItem;
import org.drools.model.flow.SetViewItem;
import org.drools.model.flow.ViewItem;
import org.drools.model.patterns.AccumulatePatternImpl;
import org.drools.model.patterns.AndPatterns;
import org.drools.model.patterns.ExistentialPatternImpl;
import org.drools.model.patterns.OrPatterns;
import org.drools.model.patterns.PatternBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewBuilder {

    private ViewBuilder() { }

    public static List<Condition> viewItems2Conditions(ViewItem[] viewItems) {
        Map<Variable, InputViewItem> inputs = new HashMap<Variable, InputViewItem>();
        Map<Variable, PatternBuilder.ValidBuilder> builderMap = new HashMap<Variable, PatternBuilder.ValidBuilder>();
        List<CombinedExprViewItem> combinedExpressions = new ArrayList<CombinedExprViewItem>();
        Set<Variable> variablesFromcombinedExpressions = new HashSet<Variable>();

        for (ViewItem viewItem : viewItems) {
            if (viewItem instanceof CombinedExprViewItem) {
                combinedExpressions.add((CombinedExprViewItem)viewItem);
                variablesFromcombinedExpressions.add(viewItem.getFirstVariable());
                continue;
            }

            Variable var = viewItem.getFirstVariable();
            if (viewItem instanceof InputViewItem) {
                inputs.put(var, (InputViewItem)viewItem);
                continue;
            }

            if (viewItem instanceof SetViewItem) {
                SetViewItem setViewItem = (SetViewItem)viewItem;
                PatternBuilder.ValidBuilder patternBuilder = setViewItem.isMultivalue() ?
                        new PatternBuilder.InvokerMultiValuePatternBuilder( var,
                                                                            DataSourceDefinitionImpl.DEFAULT,
                                                                            setViewItem.getInputVariables(),
                                                                            setViewItem.getInvokedFunction() ) :
                        new PatternBuilder.InvokerSingleValuePatternBuilder( var,
                                                                             DataSourceDefinitionImpl.DEFAULT,
                                                                             setViewItem.getInputVariables(),
                                                                             setViewItem.getInvokedFunction() );
                builderMap.put(var, patternBuilder);
                continue;
            }

            PatternBuilder.ValidBuilder patternBuilder = builderMap.get(var);
            if (patternBuilder == null) {
                patternBuilder = new PatternBuilder().filter(var)
                                                     .from(inputs.get(var).getDataSourceDefinition());
                builderMap.put(var, patternBuilder);
            }

            if (viewItem instanceof ExprViewItem) {
                builderMap.put(var, expr2PatternBuilder((ExprViewItem)viewItem, patternBuilder));
            }
        }

        for (Variable var : inputs.keySet()) {
            if (!builderMap.containsKey(var) && !variablesFromcombinedExpressions.contains(var)) {
                builderMap.put(var, new PatternBuilder().filter(var)
                                                        .from(inputs.get(var).getDataSourceDefinition()));
            }
        }

        List<Pattern> patterns = new ArrayList<Pattern>();
        for (PatternBuilder.ValidBuilder builder : builderMap.values()) {
            patterns.add(builder.get());
        }

        return aggregateConditions(inputs, combinedExpressions, patterns);
    }

    private static PatternBuilder.ValidBuilder expr2PatternBuilder(ExprViewItem viewItem, PatternBuilder.ValidBuilder patternBuilder) {
        if (viewItem instanceof Expr1ViewItem) {
            Expr1ViewItem expr = (Expr1ViewItem)viewItem;
            if (patternBuilder instanceof PatternBuilder.BoundPatternBuilder) {
                patternBuilder = ((PatternBuilder.BoundPatternBuilder) patternBuilder).with(expr.getExprId(), expr.getPredicate());
            } else if (patternBuilder instanceof PatternBuilder.ConstrainedPatternBuilder) {
                patternBuilder = ((PatternBuilder.ConstrainedPatternBuilder) patternBuilder).and(expr.getExprId(), expr.getPredicate());
            }
        } else if (viewItem instanceof Expr2ViewItem) {
            Expr2ViewItem expr = (Expr2ViewItem)viewItem;
            if (patternBuilder instanceof PatternBuilder.BoundPatternBuilder) {
                patternBuilder = ((PatternBuilder.BoundPatternBuilder)patternBuilder).with(expr.getExprId(), expr.getFirstVariable(), expr.getSecondVariable(), expr.getPredicate());
            } else if (patternBuilder instanceof PatternBuilder.ConstrainedPatternBuilder) {
                patternBuilder = ((PatternBuilder.ConstrainedPatternBuilder)patternBuilder).and(expr.getExprId(), expr.getFirstVariable(), expr.getSecondVariable(), expr.getPredicate());
            }
        } else if (viewItem instanceof AccumulateExprViewItem) {
            AccumulateExprViewItem acc = (AccumulateExprViewItem)viewItem;
            patternBuilder = new AccumulateBuilder(expr2PatternBuilder(acc.getExpr(), patternBuilder), acc.getFunctions());
        }

        if (viewItem.getExistentialType() == ExistentialPattern.ExistentialType.NOT) {
            patternBuilder = new NotBuilder(patternBuilder);
        } else if (viewItem.getExistentialType() == ExistentialPattern.ExistentialType.EXISTS) {
            patternBuilder = new ExistsBuilder(patternBuilder);
        }
        return patternBuilder;
    }

    private static Condition createPatternForCombinedExpression(Map<Variable, InputViewItem> inputs, CombinedExprViewItem combinedExpression) {
        List<CombinedExprViewItem> combinedExpressions = new ArrayList<CombinedExprViewItem>();
        List<Pattern> patterns = new ArrayList<Pattern>();

        for (ExprViewItem viewItem : combinedExpression.getExpressions()) {
            if (viewItem instanceof CombinedExprViewItem) {
                combinedExpressions.add((CombinedExprViewItem)viewItem);
                continue;
            }
            Variable var = viewItem.getFirstVariable();
            if (viewItem instanceof Expr1ViewItem) {
                Expr1ViewItem expr = (Expr1ViewItem)viewItem;
                Pattern pattern = new PatternBuilder().filter(var)
                                                      .from(inputs.get(var).getDataSourceDefinition())
                                                      .with(expr.getPredicate())
                                                      .get();
                patterns.add(pattern);
            } else if (viewItem instanceof Expr2ViewItem) {
                Expr2ViewItem expr = (Expr2ViewItem)viewItem;
                Pattern pattern = new PatternBuilder().filter(var)
                                                      .from(inputs.get(var).getDataSourceDefinition())
                                                      .with(expr.getFirstVariable(), expr.getSecondVariable(), expr.getPredicate())
                                                      .get();
                patterns.add(pattern);
            }
        }

        List<Condition> conditions = aggregateConditions(inputs, combinedExpressions, patterns);
        if (combinedExpression.getType() instanceof Condition.AndType) {
            return new AndPatterns(conditions.toArray(new Condition[conditions.size()]));
        } else if (combinedExpression.getType() instanceof Condition.OrType) {
            return new OrPatterns(conditions.toArray(new Condition[conditions.size()]));
        }
        throw new RuntimeException("Unknown expression type: " + combinedExpression.getType());
    }

    private static List<Condition> aggregateConditions(Map<Variable, InputViewItem> inputs, List<CombinedExprViewItem> combinedExpressions, List<Pattern> patterns) {
        Collections.sort(patterns, PATTERN_DEPS_COMPARATOR);
        List<Condition> conditions = new ArrayList<Condition>();
        conditions.addAll(patterns);
        for (CombinedExprViewItem combinedExpression : combinedExpressions) {
            conditions.add(createPatternForCombinedExpression(inputs, combinedExpression));
        }
        return conditions;
    }

    private static final PatternDependencyComparator PATTERN_DEPS_COMPARATOR = new PatternDependencyComparator();
    private static class PatternDependencyComparator implements Comparator<Pattern> {
        @Override
        public int compare(Pattern p1, Pattern p2) {
            for (Variable p2Input : p2.getInputVariables()) {
                if (!p2.getPatternVariable().equals(p2Input) && p1.getPatternVariable().equals(p2Input)) {
                    return -1;
                }
            }
            for (Variable p1Input : p1.getInputVariables()) {
                if (!p1.getPatternVariable().equals(p1Input) && p2.getPatternVariable().equals(p1Input)) {
                    return 1;
                }
            }
            return p1.getInputVariables().length - p2.getInputVariables().length;
        }
    }

    private static class NotBuilder implements PatternBuilder.ValidBuilder {
        private final PatternBuilder.ValidBuilder builder;

        private NotBuilder(PatternBuilder.ValidBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Pattern get() {
            return new ExistentialPatternImpl(ExistentialPattern.ExistentialType.NOT, builder.get());
        }
    }

    private static class ExistsBuilder implements PatternBuilder.ValidBuilder {
        private final PatternBuilder.ValidBuilder builder;

        private ExistsBuilder(PatternBuilder.ValidBuilder builder) {
            this.builder = builder;
        }

        @Override
        public Pattern get() {
            return new ExistentialPatternImpl(ExistentialPattern.ExistentialType.EXISTS, builder.get());
        }
    }

    private static class AccumulateBuilder implements PatternBuilder.ValidBuilder {
        private final PatternBuilder.ValidBuilder builder;
        private final AccumulateFunction[] functions;

        private AccumulateBuilder(PatternBuilder.ValidBuilder builder, AccumulateFunction[] functions) {
            this.builder = builder;
            this.functions = functions;
        }

        @Override
        public Pattern get() {
            return new AccumulatePatternImpl(builder.get(), functions);
        }
    }
}
