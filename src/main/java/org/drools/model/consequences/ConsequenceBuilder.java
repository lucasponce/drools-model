package org.drools.model.consequences;

import org.drools.model.Consequence;
import org.drools.model.Variable;
import org.drools.model.functions.Block0;
import org.drools.model.functions.Block1;
import org.drools.model.functions.Block2;
import org.drools.model.functions.BlockN;
import org.drools.model.functions.Function0;
import org.drools.model.functions.Function1;
import org.drools.model.functions.Function2;
import org.drools.model.functions.FunctionN;

import java.util.ArrayList;
import java.util.List;

public class ConsequenceBuilder {

    public _0 execute(Block0 block) {
        return new _0(block);
    }

    public <A> _1<A> on(Variable<A> dec1) {
        return new _1(dec1);
    }

    public <A, B> _2<A, B> on(Variable<A> decl1, Variable<B> decl2) {
        return new _2(decl1, decl2);
    }

    public interface ValidBuilder {
        Consequence get();
    }

    public static abstract class AbstractValidBuilder implements ValidBuilder {
        private final Variable[] declarations;
        protected BlockN block;
        private List<FunctionN> inserts = new ArrayList<FunctionN>();
        private List<Consequence.Update> updates = new ArrayList<Consequence.Update>();
        private Variable[] deletes;

        protected AbstractValidBuilder(Variable... declarations) {
            this.declarations = declarations;
        }

        @Override
        public Consequence get() {
            return new ConsequenceImpl(block,
                                       declarations,
                                       inserts.toArray(new FunctionN[inserts.size()]),
                                       updates.toArray(new Consequence.Update[updates.size()]),
                                       deletes);
        }

        public AbstractValidBuilder update(Variable updatedVariable, String... updatedFields) {
            updates.add(new ConsequenceImpl.UpdateImpl(updatedVariable, updatedFields));
            return this;
        }

        public AbstractValidBuilder delete(Variable... deletes) {
            this.deletes = deletes;
            return this;
        }

        protected void addInsert(FunctionN f) {
            inserts.add(f);
        }
    }

    public static class _0 extends AbstractValidBuilder {
        public _0(final Block0 block) {
            super(new Variable[0]);
            this.block = new BlockN() {
                @Override
                public void execute(Object... objs) {
                    block.execute();
                }
            };
        }

        public <R> _0 insert(final Function0<R> f) {
            addInsert(new FunctionN() {
                @Override
                public R apply(Object... objs) {
                    return f.apply();
                }
            });
            return this;
        }
    }

    public static class _1<A> extends AbstractValidBuilder {
        private _1(Variable<A> declaration) {
            super(declaration);
        }

        public _1<A> execute(final Block1<A> block) {
            this.block = new BlockN() {
                @Override
                public void execute(Object... objs) {
                    block.execute((A)objs[0]);
                }
            };
            return this;
        }

        public <R> _1 insert(final Function1<A, R> f) {
            addInsert(new FunctionN() {
                @Override
                public R apply(Object... objs) {
                    return f.apply((A)objs[0]);
                }
            });
            return this;
        }
    }

    public static class _2<A, B> extends AbstractValidBuilder {
        private _2(Variable<A> decl1, Variable<B> decl2) {
            super(decl1, decl2);
        }

        public _2<A, B> execute(final Block2<A, B> block) {
            this.block = new BlockN() {
                @Override
                public void execute(Object... objs) {
                    block.execute((A)objs[0], (B)objs[1]);
                }
            };
            return this;
        }

        public <R> _2 insert(final Function2<A, B, R> f) {
            addInsert(new FunctionN() {
                @Override
                public R apply(Object... objs) {
                    return f.apply((A)objs[0], (B)objs[1]);
                }
            });
            return this;
        }
    }
}
