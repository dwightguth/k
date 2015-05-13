// Copyright (c) 2014-2015 K Team. All Rights Reserved.
package org.kframework.parser.concrete2kore.kernel;

import com.google.common.collect.Sets;
import dk.brics.automaton.RegExp;
import dk.brics.automaton.RunAutomaton;
import org.junit.Assert;
import org.junit.Test;
import org.kframework.attributes.Att;
import org.kframework.builtin.Sorts;
import org.kframework.definition.Production;
import org.kframework.definition.ProductionItem;
import org.kframework.kore.Sort;
import org.kframework.parser.Ambiguity;
import org.kframework.parser.Constant;
import org.kframework.parser.KList;
import org.kframework.parser.Term;
import org.kframework.parser.TermCons;
import org.kframework.parser.concrete2kore.disambiguation.TreeCleanerVisitor;
import org.kframework.parser.concrete2kore.kernel.Grammar.NonTerminal;
import org.kframework.parser.concrete2kore.kernel.Grammar.NonTerminalState;
import org.kframework.parser.concrete2kore.kernel.Grammar.RegExState;
import org.kframework.parser.concrete2kore.kernel.Grammar.RuleState;
import org.kframework.parser.concrete2kore.kernel.Rule.DeleteRule;
import org.kframework.parser.concrete2kore.kernel.Rule.WrapLabelRule;
import org.pcollections.ConsPStack;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.kframework.Collections.*;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

public class ParserTest {
    /* public static void main(String[] args) {
        try {
            System.in.read();
            new ParserTest().testListOfTokens();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void setUp() throws Exception {
        super.setUp();

    }*/

    private static final Sort EXP_SORT = Sort("Exp");

    @Test
    public void testEmptyGrammar() throws Exception {
        Grammar grammar = new Grammar();
        NonTerminal nt1 = new NonTerminal("startNt");
        nt1.entryState.next.add(nt1.exitState);
        grammar.add(nt1);

        grammar.compile();

        Parser parser = new Parser("");
        Term result = parser.parse(grammar.get("startNt"), 0);
        Term expected = amb(klist(amb(KList.apply(ConsPStack.empty()))));

        Assert.assertEquals("Empty Grammar check: ", expected, result);
        // the start and exit state of the NonTerminal
        grammar.add(nt1);
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testSingleToken() throws Exception {
        NonTerminal nt1 = new NonTerminal("StartNT");
        RegExState res = new RegExState("RegExStid", nt1, regex("[a-zA-Z0-9]+"), new HashSet<>());
        RuleState rs = new RuleState("RuleStateId", nt1, new WrapLabelRule(constant("seq")), new HashSet<>());
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        nt1.entryState.next.add(res);
        res.next.add(rs);
        rs.next.add(nt1.exitState);

        grammar.compile();
        Parser parser = new Parser("asdfAAA1");

        Term result = parser.parse(nt1, 0);
        Term expected = amb(klist(amb(klist(Constant.apply("asdfAAA1", constant("seq"))))));
        Assert.assertEquals("Single Token check: ", expected, result);
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(nt1));
    }

    @Test
    public void testListOfTokens() throws Exception {
        // A ::= ("[a-zA-Z0-9]")*  [klabel(seq)]
        Production prd = constant("seq");

        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState res1 = new RegExState("RegExStid", nt1, regex("[a-zA-Z0-9]"), new HashSet<>());
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(prd), new HashSet<>());

        nt1.entryState.next.add(res1);
        nt1.entryState.next.add(rs3);
        res1.next.add(rs3);
        res1.next.add(res1);
        rs3.next.add(nt1.exitState);
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("abc").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(Constant.apply("abc", prd)))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(Constant.apply("", prd)))));
            Assert.assertEquals("Single Token check: ", expected, result);
        }

        /*
        {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                sb.append('a');
            }
            for (int j = 0; j < 20; j++) {
                long start = getCpuTime();
                for (int i = 0; i < 100; i++) {
                    Term result = new Parser(sb.toString()).parse(nt1, 0);
                }
                long end = getCpuTime();
                System.out.println("Time: " + ((end - start) / 1000000.0));
            }
        }
        */
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }
    public static long getCpuTime( ) {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean( );
        return bean.isCurrentThreadCpuTimeSupported( ) ?
                bean.getCurrentThreadCpuTime( ) : 0L;
    }

    @Test
    public void testNestedNonTerminals1() throws Exception {
        // A ::= ""    [klabel(epsilon)]
        //     | x A y [klabel(xAy)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resx = new RegExState("RegExStidx", nt1, regex("x"), new HashSet<>());
        RuleState delx = new RuleState("X-Delete", nt1, new DeleteRule(1), new HashSet<>());
        RegExState resy = new RegExState("RegExStidy", nt1, regex("y"), new HashSet<>());
        RuleState dely = new RuleState("Y-Delete", nt1, new DeleteRule(1), new HashSet<>());
        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("xAy")), new HashSet<>());
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("epsilon")), new HashSet<>());

        NonTerminalState nts = new NonTerminalState("NT", nt1, nt1, false, new HashSet<>());

        nt1.entryState.next.add(resx);
        nt1.entryState.next.add(rs3);
        rs3.next.add(nt1.exitState);
        resx.next.add(delx);
        delx.next.add(nts);
        nts.next.add(resy);
        resy.next.add(dely);
        dely.next.add(rs1);
        rs1.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("epsilon")))));
            Assert.assertEquals("EmtpyString check: ", expected, result);
        }

        {
            Term result = new Parser("xxyy").parse(nt1, 0);
            Term expected =
                amb(klist(amb(klist(kapp("xAy",
                    amb(klist(kapp("xAy",
                            amb(klist(kapp("epsilon")))))))))));
            Assert.assertEquals("x^ny^n check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals2() throws Exception {
        // A ::= ""  [klabel(epsilon)]
        //     | A y [klabel(Ay)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resy = new RegExState("RegExStidy", nt1, regex("y"), new HashSet<>());
        RuleState dely = new RuleState("Y-Delete", nt1, new DeleteRule(1), new HashSet<>());

        NonTerminalState nts = new NonTerminalState("NT", nt1, nt1, false, new HashSet<>());
        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("Ay")), new HashSet<>());
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("epsilon")), new HashSet<>());

        nt1.entryState.next.add(nts);
        nt1.entryState.next.add(rs3);
        rs3.next.add(nt1.exitState);
        nts.next.add(resy);
        resy.next.add(dely);
        dely.next.add(rs1);
        rs1.next.add(nt1.exitState);
        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("epsilon")))));
            Assert.assertEquals("EmtpyString check: ", expected, result);
        }

        {
            Term result = new Parser("yy").parse(nt1, 0);
            Term expected =
                amb(klist(amb(klist(kapp("Ay",
                    amb(klist(kapp("Ay",
                            amb(klist(kapp("epsilon")))))))))));
            Assert.assertEquals("y^n check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals3() throws Exception {
        // A ::= ""  [klabel(epsilon)]
        //     | x A [klabel(xA)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resx = new RegExState("RegExStidx", nt1, regex("x"), new HashSet<>());
        RuleState delx = new RuleState("X-Delete", nt1, new DeleteRule(1), new HashSet<>());

        NonTerminalState nts = new NonTerminalState("NT", nt1, nt1, false, new HashSet<>());
        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("xA")), new HashSet<>());
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("epsilon")), new HashSet<>());

        nt1.entryState.next.add(resx);
        nt1.entryState.next.add(rs3);
        rs3.next.add(nt1.exitState);
        resx.next.add(delx);
        delx.next.add(nts);
        nts.next.add(rs1);
        rs1.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("epsilon")))));
            Assert.assertEquals("EmtpyString check: ", expected, result);
        }

        {
            Term result = new Parser("xx").parse(nt1, 0);
            Term expected =
                amb(klist(amb(klist(kapp("xA",
                        amb(klist(kapp("xA",
                                amb(klist(kapp("epsilon")))))))))));
            Assert.assertEquals("x^n check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals4() throws Exception {
        // A ::= "x" [klabel(x)]
        //     | A A [klabel(AA)]
        NonTerminal nt1 = new NonTerminal("StartNT");

        RegExState resx = new RegExState("RegExStidx", nt1,regex("x"), new HashSet<>());
        RuleState delx = new RuleState("X-Delete", nt1, new DeleteRule(1), new HashSet<>());

        NonTerminalState nts1 = new NonTerminalState("NT1", nt1, nt1, false, new HashSet<>());
        NonTerminalState nts2 = new NonTerminalState("NT2", nt1, nt1, false, new HashSet<>());

        RuleState rs1 = new RuleState("RuleStateId1", nt1, new WrapLabelRule(label("x")), new HashSet<>());
        RuleState rs3 = new RuleState("RuleStateId2", nt1, new WrapLabelRule(label("AA")), new HashSet<>());


        nt1.entryState.next.add(resx);
        nt1.entryState.next.add(nts1);

        resx.next.add(delx);
        delx.next.add(rs1);
        rs1.next.add(nt1.exitState);

        nts1.next.add(nts2);
        nts2.next.add(rs3);
        rs3.next.add(nt1.exitState);

        Grammar grammar = new Grammar();
        grammar.add(nt1);
        grammar.compile();

        {
            Term result = new Parser("x").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("x")))));
            Assert.assertEquals("Single char check: ", expected, result);
        }

        {
            Term result = new Parser("xx").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("AA", amb(klist(kapp("x"))), amb(klist(kapp("x"))))))));
            Assert.assertEquals("AA check: ", expected, result);
        }
        Term X = kapp("x");
        {
            Term result = new Parser("xxx").parse(nt1, 0);
            Term expected = amb(klist(amb(klist(kapp("AA", amb(klist(kapp("AA", amb(klist(X)), amb(klist(X))))), amb(klist(X)))),
                                          klist(kapp("AA", amb(klist(X)), amb(klist(kapp("AA", amb(klist(X)), amb(klist(X))))))))));
            Assert.assertEquals("AAA check: ", expected, result);
        }
        {
            Term result = new Parser("xxxx").parse(nt1, 0);
            Term t1 = amb(klist(X));
            Term t2 = amb(klist(kapp("AA", t1, t1)));
            Term t3 = amb(klist(kapp("AA", t2, t1)), klist(kapp("AA", t1, t2)));
            Term t4 = amb(klist(kapp("AA", t3, t1)), klist(kapp("AA", t2, t2)), klist(kapp("AA", t1, t3)));
            Term expected = amb(klist(t4));
            Assert.assertEquals("AAA check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(nt1.entryState) && nc.isNullable(nt1.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(nt1));
    }

    @Test
    public void testNestedNonTerminals5() throws Exception {
        // A1 ::= "x"  [klabel(x)]
        // A2 ::= A1   [klabel(n1)]
        // ....
        // An ::= An-1 [klabel(n[n-1])]
        // start symb is An
        NonTerminal baseCase = new NonTerminal("BaseCase");
        RegExState resx = new RegExState("X", baseCase, regex("x"), new HashSet<>());
        RuleState rs1 = new RuleState("RuleStateId1", baseCase, new WrapLabelRule(label("x")), new HashSet<>());
        RuleState delx = new RuleState("X-Delete", baseCase, new DeleteRule(1), new HashSet<>());

        baseCase.entryState.next.add(resx);
        resx.next.add(delx);
        delx.next.add(rs1);
        rs1.next.add(baseCase.exitState);

        Term expected = amb(klist(kapp("x")));

        for (int i = 2; i < 10; i++) {
            NonTerminal nt = new NonTerminal("NT"+i);
            NonTerminalState state = new NonTerminalState("S"+i, nt, baseCase, false, new HashSet<>());
            RuleState rs2 = new RuleState("RuleStateId" + i, nt, new WrapLabelRule(label("n" + i)), new HashSet<>());
            nt.entryState.next.add(state);
            state.next.add(rs2);
            rs2.next.add(nt.exitState);
            baseCase = nt;
            expected = amb(klist(kapp("n" + i, expected)));
        }
        Grammar grammar = new Grammar();
        grammar.add(baseCase);
        grammar.compile();

        {
            Term result = new Parser("x").parse(baseCase, 0);
            expected = amb(klist(expected));
            Assert.assertEquals("Single char check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(baseCase.entryState) && nc.isNullable(baseCase.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(baseCase));
    }

    @Test
    public void testNestedNonTerminals6() throws Exception {
        // A1 ::= ""  [klabel(x)]
        // A2 ::= A1   [klabel(n1)]
        // ....
        // An ::= An-1 [klabel(n[n-1])]
        // start symb is An

        NonTerminal baseCase = new NonTerminal("BaseCase");
        RegExState resx = new RegExState("X", baseCase, regex(""), new HashSet<>());
        RuleState rs1 = new RuleState("RuleStateId1", baseCase, new WrapLabelRule(label("x")), new HashSet<>());
        RuleState delx = new RuleState("X-Delete", baseCase, new DeleteRule(1), new HashSet<>());

        baseCase.entryState.next.add(resx);
        resx.next.add(delx);
        delx.next.add(rs1);
        rs1.next.add(baseCase.exitState);

        Term expected = amb(klist(kapp("x")));

        for (int i = 2; i < 10; i++) {
            NonTerminal nt = new NonTerminal("NT"+i);
            NonTerminalState state = new NonTerminalState("S"+i, nt, baseCase, false, new HashSet<>());
            RuleState rs2 = new RuleState("RuleStateId" + i, nt, new WrapLabelRule(label("n" + i)), new HashSet<>());
            nt.entryState.next.add(state);
            state.next.add(rs2);
            rs2.next.add(nt.exitState);
            baseCase = nt;
            expected = amb(klist(kapp("n" + i, expected)));
        }
        Grammar grammar = new Grammar();
        grammar.add(baseCase);
        grammar.compile();

        {
            Term result = new Parser("").parse(baseCase, 0);
            expected = amb(klist(expected));
            Assert.assertEquals("Single char check: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(baseCase.entryState) && nc.isNullable(baseCase.exitState));
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(baseCase));
    }

    @Test
    public void testArithmeticLanguage() throws Exception {
        // Lit  ::= Token{[0-9]+}[klabel(lit)]
        // Term ::= "(" Exp ")"  [klabel(bracket)]
        //        | Term "*" Lit [klabel(mul)]
        //        | Lit
        // Exp  ::= Exp "+" Term [klabel(plus)]
        //        | Term
        NonTerminal lit = new NonTerminal("Lit");
        NonTerminal trm = new NonTerminal("Trm");
        NonTerminal exp = new NonTerminal("Exp");

        Production litPrd = constant("lit");
        { // lit
            RegExState litState = new RegExState("LitState", lit, regex("[0-9]+"), new HashSet<>());
            RuleState rs1 = new RuleState("RuleStateId1", lit, new WrapLabelRule(litPrd), new HashSet<>());
            lit.entryState.next.add(litState);
            litState.next.add(rs1);
            rs1.next.add(lit.exitState);
        }

        { // trm
            RegExState lparen = new RegExState("LParen", trm, regex("\\("), new HashSet<>());
            RuleState delLparen = new RuleState("LParen-Delete", trm, new DeleteRule(1), new HashSet<>());
            RegExState rparen = new RegExState("RParen", trm, regex("\\)"), new HashSet<>());
            RuleState delRparen = new RuleState("RParen-Delete", trm, new DeleteRule(1), new HashSet<>());
            RuleState rs1 = new RuleState("RuleStateId1", trm, new WrapLabelRule(label("bracket")), new HashSet<>());

            RegExState star = new RegExState("Star", trm, regex("\\*"), new HashSet<>());
            RuleState delStar = new RuleState("Star-Delete", trm, new DeleteRule(1), new HashSet<>());
            NonTerminalState expState = new NonTerminalState("Trm->Exp", trm, exp, false, new HashSet<>());
            NonTerminalState trmState = new NonTerminalState("Trm->Trm", trm, trm, false, new HashSet<>());
            NonTerminalState lit1State = new NonTerminalState("Trm->Lit1", trm, lit, false, new HashSet<>());
            RuleState rs2 = new RuleState("RuleStateId2", trm, new WrapLabelRule(label("mul")), new HashSet<>());

            NonTerminalState lit2State = new NonTerminalState("Trm->Lit2", trm, lit, false, new HashSet<>());

            trm.entryState.next.add(lparen);
            lparen.next.add(delLparen);
            delLparen.next.add(expState);
            expState.next.add(rparen);
            rparen.next.add(delRparen);
            delRparen.next.add(rs1);
            rs1.next.add(trm.exitState);

            trm.entryState.next.add(trmState);
            trmState.next.add(star);
            star.next.add(delStar);
            delStar.next.add(lit1State);
            lit1State.next.add(rs2);
            rs2.next.add(trm.exitState);

            trm.entryState.next.add(lit2State);
            lit2State.next.add(trm.exitState);
        }

        { // exp
            RegExState plus = new RegExState("Plus", exp, regex("\\+"), new HashSet<>());
            RuleState delPlus = new RuleState("Plus-Delete", exp, new DeleteRule(1), new HashSet<>());
            NonTerminalState expState = new NonTerminalState("Exp->Exp", exp, exp, false, new HashSet<>());
            NonTerminalState trm1State = new NonTerminalState("Exp->Trm1", exp, trm, false, new HashSet<>());
            RuleState rs1 = new RuleState("RuleStateId3", exp, new WrapLabelRule(label("plus")), new HashSet<>());
            NonTerminalState trm2State = new NonTerminalState("Exp->Trm2", exp, trm, false, new HashSet<>());

            exp.entryState.next.add(expState);
            expState.next.add(plus);
            plus.next.add(delPlus);
            delPlus.next.add(trm1State);
            trm1State.next.add(rs1);
            rs1.next.add(exp.exitState);

            exp.entryState.next.add(trm2State);
            trm2State.next.add(exp.exitState);
        }

        Grammar grammar = new Grammar();
        grammar.add(exp);
        grammar.compile();
        {
            Term result = new Parser("1+2*3").parse(exp, 0);
            Term expected = amb(klist(amb(klist(kapp("plus", amb(klist(amb(klist(amb(klist(Constant.apply("1", litPrd))))))),
                                                amb(klist(kapp("mul", amb(klist(amb(klist(Constant.apply("2", litPrd))))),
                                                          amb(klist(Constant.apply("3", litPrd)))))))))));
            Assert.assertEquals("1+2*3: ", expected, result);
        }
        Nullability nc = new Nullability(grammar) ;
        Assert.assertEquals("Expected Nullable NTs", true, nc.isNullable(exp.entryState) && nc.isNullable(exp.exitState));
        Assert.assertEquals("Expected Nullable NTs", false, nc.isNullable(exp));

    }

    @Test
    public void testListAmbiguity() throws Exception {
        // Int
        // (|-----([\+-]?\d+)------|)
        NonTerminal intNt = new NonTerminal("Int");
        Production intProd09 = constant("Int");
        RegExState ints = new RegExState("Int-State", intNt, regex("[\\+\\-]?[0-9]+"), new HashSet<>());
        RuleState intLabel = new RuleState("Exps-wrapMinus", intNt, new WrapLabelRule(intProd09), new HashSet<>());
        intNt.entryState.next.add(ints);
        ints.next.add(intLabel);
        intLabel.next.add(intNt.exitState);

        // Exp
        /**
         * (|--+---[Int]------<wrap>--------------+---|)
         *     |                                  |
         *     +---("-")---<del>---[Exp]---<'-_>--+
         */
        NonTerminal expNt = new NonTerminal("Exp");

        NonTerminalState expInt = new NonTerminalState("Int-nts(Exp)", expNt, intNt, false, new HashSet<>());
        Production p22 = prod(EXP_SORT, NonTerminal(Sorts.Int()));
        RuleState rs2 = new RuleState("Exp-wrapInt", expNt, new WrapLabelRule(p22), new HashSet<>());
        expNt.entryState.next.add(expInt);
        expInt.next.add(rs2);
        rs2.next.add(expNt.exitState);

        RegExState minus = new RegExState("Minus-State", expNt, regex("\\-"), new HashSet<>());
        RuleState deleteToken = new RuleState("Minus-Delete", expNt, new DeleteRule(1), new HashSet<>());
        NonTerminalState expExp = new NonTerminalState("Exp-nts(Exp)", expNt, expNt, false, new HashSet<>());
        Production p1 = Production(EXP_SORT, Seq(Terminal("-"), NonTerminal(EXP_SORT)), Attributes().add("#klabel", "'-_"));
        RuleState rs1 = new RuleState("Exps-wrapMinus", expNt, new WrapLabelRule(p1), new HashSet<>());
        expNt.entryState.next.add(minus);
        minus.next.add(deleteToken);
        deleteToken.next.add(expExp);
        expExp.next.add(rs1);
        rs1.next.add(expNt.exitState);

        // Exps
        /**
         * (|-------[Exp]--------------+----<'_,_>-------|)
         *            ^                |
         *            +--<del>--(",")--+
         */
        NonTerminal expsNt = new NonTerminal("Exps");
        NonTerminalState expExps = new NonTerminalState("Exp-nts(Exps)", expsNt, expNt, false, new HashSet<>());
        Production p2 = Production(Sort("Exps"), Seq(NonTerminal(EXP_SORT)), Attributes().add("#klabel", "'_,_"));
        RegExState separator = new RegExState("Sep-State", expsNt, regex("\\,"), new HashSet<>());
        RuleState deleteToken2 = new RuleState("Separator-Delete", expsNt, new DeleteRule(1), new HashSet<>());
        RuleState labelList = new RuleState("RuleStateExps", expsNt, new WrapLabelRule(p2), new HashSet<>());
        expsNt.entryState.next.add(expExps);
        expExps.next.add(expExps); // circularity
        separator.next.add(deleteToken2);
        deleteToken2.next.add(expExps);
        expExps.next.add(labelList);
        labelList.next.add(expsNt.exitState);

        Grammar grammar = new Grammar();
        grammar.add(expsNt);
        grammar.compile();

        Term result = new Parser("-1").parse(expsNt, 0);
        //System.out.println(result);
        Term result2 = new TreeCleanerVisitor().apply(result).right().get();
        //System.out.println(result2);

        Term one = Constant.apply("1", intProd09);
        Term mone = Constant.apply("-1", intProd09);
        Term mexp = TermCons.apply(ConsPStack.singleton(one), p1);
        Term expected = TermCons.apply(ConsPStack.singleton(amb(mone, mexp)), p2);

        Assert.assertEquals("The error: ", expected, result2);
    }

    public static Ambiguity amb(Term ... terms) {
        return Ambiguity.apply(Sets.newHashSet(terms));
    }

    public static Production prod(Sort sort, ProductionItem... pi) {
        return Production(sort, immutable(Arrays.asList(pi)));
    }

    public static Production tokenProd(Sort sort, ProductionItem... pi) {
        return Production(sort, immutable(Arrays.asList(pi)), new Att(Set(KApply(KLabel("token"), KList()))));
    }

    public static TermCons kapp(String label, Term ... terms) {
        List<Term> x = Arrays.asList(terms);
        Collections.reverse(x);
        return TermCons.apply(ConsPStack.from(x), label(label));
    }

    public static KList klist(Term ... terms) {
        return KList.apply(ConsPStack.from(Arrays.asList(terms)));
    }

    public static Production constant(String x) {
        return tokenProd(Sorts.K(), Terminal(x));
    }

    public static Production label(String x) {
        return Production(x, Sorts.K(), Seq(NonTerminal(Sorts.K())));
    }

    public static RunAutomaton regex(String x) {
        return new RunAutomaton(new RegExp(x).toAutomaton(), false);
    }
}
