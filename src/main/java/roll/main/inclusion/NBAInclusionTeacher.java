/* Copyright (c) 2016, 2017                                               */
/*       Institute of Software, Chinese Academy of Sciences               */
/* This file is part of ROLL, a Regular Omega Language Learning library.  */
/* ROLL is free software: you can redistribute it and/or modify           */
/* it under the terms of the GNU General Public License as published by   */
/* the Free Software Foundation, either version 3 of the License, or      */
/* (at your option) any later version.                                    */

/* This program is distributed in the hope that it will be useful,        */
/* but WITHOUT ANY WARRANTY; without even the implied warranty of         */
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          */
/* GNU General Public License for more details.                           */

/* You should have received a copy of the GNU General Public License      */
/* along with this program.  If not, see <http://www.gnu.org/licenses/>.  */

package roll.main.inclusion;

import automata.FiniteAutomaton;
import dk.brics.automaton.Automaton;
import mainfiles.RABIT;
import roll.automata.FDFA;
import roll.automata.NBA;
import roll.automata.operations.FDFAOperations;
import roll.automata.operations.NBAIntersectCheck;
import roll.automata.operations.NBAOperations;
import roll.main.Options;
import roll.oracle.Teacher;
import roll.oracle.nba.sampler.NBAInclusionSampler;
import roll.oracle.nba.sampler.SamplerIndexedMonteCarlo;
import roll.parser.ba.BAParser;
import roll.query.Query;
import roll.query.QuerySimple;
import roll.table.HashableValue;
import roll.table.HashableValueBoolean;
import roll.util.Pair;
import roll.util.Timer;
import roll.words.Alphabet;
import roll.words.Word;

/**
 * @author Yong Li (liyong@ios.ac.cn)
 * */

public class NBAInclusionTeacher implements Teacher<FDFA, Query<HashableValue>, HashableValue>{

    private final Options options;
    private final Alphabet alphabet;
    private final NBA A;
    private final NBA B;
    private final BAParser parser;
    private final FiniteAutomaton rB;
    
    public NBAInclusionTeacher(Options options, BAParser parser, NBA A, NBA B) {
        assert options != null && parser!= null && A != null && B != null;
        this.options = options;
        this.parser = parser;
        this.A = A;
        this.B = B;
        this.alphabet = A.getAlphabet();
        this.rB = UtilInclusion.toRABITNBA(B);
    }
    
    @Override
    public HashableValue answerMembershipQuery(Query<HashableValue> query) {
        Timer timer = new Timer();
        timer.start();
        
        boolean result;
        Word prefix = query.getPrefix();
        Word suffix = query.getSuffix();
        
        if(suffix.isEmpty()) {
            result = false;
        }else {
            result = NBAOperations.accepts(B, prefix, suffix);
        }
        
        // if uv is not in B
        boolean terminate = false;
        if(! result && ! suffix.isEmpty()) {
            terminate = NBAOperations.accepts(A, prefix, suffix);
        }
        
        timer.stop();
        options.stats.timeOfMembershipQuery += timer.getTimeElapsed();
        ++ options.stats.numOfMembershipQuery; 
        if(terminate) {
            options.log.println("Not included");
            System.out.println("prefix: " + parser.translate(prefix));
            System.out.println("loop: " + parser.translate(suffix));
            System.exit(0);
        }
        return new HashableValueBoolean(!result); // reverse the result for Buechi automaton
    }
    
    private int numInterBandBF;
    private long timeInterBandBF;
    
    private int numInterAandBF;
    private long timeInterAandBF;
    
    private int numInterBFCandBF;
    private long timeInterBFCandBF;
    
    private int numBFCLessB;
    private long timeBFCLessB;

    @Override
    public Query<HashableValue> answerEquivalenceQuery(FDFA hypothesis) {
        Timer timer = new Timer();
        timer.start();
        options.log.println("Translating FDFA to Under Buechi automaton ...");
        Automaton dkBF = FDFAOperations.buildUnderNBA(hypothesis);
        NBA BF = NBAOperations.fromDkNBA(dkBF, alphabet);
        ++ this.numInterBandBF;
        options.log.println("Checking the intersection of BF (" + BF.getStateSize() + ") and B ("+ B.getStateSize() + ")...");
        long t = timer.getCurrentTime();
        NBAIntersectCheck interCheck = new NBAIntersectCheck(BF, B, true);
        boolean isEmpty = interCheck.isEmpty();
        t = timer.getCurrentTime() - t;
        this.timeInterBandBF += t;
        if(options.verbose) {
            options.log.println("Hypothesis for complementation B");
            options.log.println(BF.toString());
        }
        Word prefix = null;
        Word suffix = null;
        boolean isEq = false, isInTarget = false;
        if(! isEmpty) {
            // we have omega word in FDFA which should not be there
            interCheck.computePath();
            Pair<Word, Word> pair = interCheck.getCounterexample();
            prefix = pair.getLeft();
            suffix = pair.getRight();
            isEq = false;
            isInTarget = true;
        }else {
            // intersection check for A and B(F)
            ++ this.numInterAandBF;
            options.log.println("Checking the intersection of A (" + A.getStateSize() + ") and B(F) ("+ BF.getStateSize() + ")...");
            t = timer.getCurrentTime();
            interCheck = new NBAIntersectCheck(A, BF, true);
            isEmpty = interCheck.isEmpty();
            t = timer.getCurrentTime() - t;
            this.timeInterAandBF += t;
            
            if(! isEmpty) {
                // we have found counterexample now
                interCheck.computePath();
                Pair<Word, Word> pair = interCheck.getCounterexample();
                prefix = pair.getLeft();
                suffix = pair.getRight();
                isEq = true;
                options.log.println("Inclusion violated... ");
                options.log.println("prefix: " + parser.translate(prefix));
                options.log.println("loop: " + parser.translate(suffix));
            }else {
                Automaton dkBFC = FDFAOperations.buildNegNBA(hypothesis);
                NBA BFC = NBAOperations.fromDkNBA(dkBFC, alphabet);
                options.log.println("Checking the intersection for B(F) (" + BF.getStateSize() + ") and B(F^c) ("+ BFC.getStateSize() + ")...");
                ++ this.numInterBFCandBF;
                t = timer.getCurrentTime();
                interCheck = new NBAIntersectCheck(BFC, BF, true);
                isEmpty = interCheck.isEmpty();
                t = timer.getCurrentTime() - t;
                this.timeInterBFCandBF += t;
                
                if(! isEmpty) {
                    // we have found counterexample now
                    interCheck.computePath();
                    Pair<Word, Word> pair = interCheck.getCounterexample();
                    prefix = pair.getLeft();
                    suffix = pair.getRight();
                    isEq = false;
                    isInTarget = NBAOperations.accepts(B, prefix, suffix);
                }else {
                
                    // we have to resort to the equivalence check for hypothesisNotA
                    ++this.numBFCLessB;
                    options.log.println("Checking the inclusion between B(F^c) (" + BFC.getStateSize() + ") and B ("+ B.getStateSize() + ")...");
                    if(options.verbose) {
                        options.log.println("B(F^c): \n" + BFC.toString());
                    }
                    // by sampler
                    SamplerIndexedMonteCarlo sampler = new SamplerIndexedMonteCarlo(options.epsilon, options.delta);
                    sampler.K = B.getStateSize();
                    Query<HashableValue> ceQuery = NBAInclusionSampler.isIncluded(BFC, B, sampler);
                    
                    if(ceQuery != null) {
                        prefix = ceQuery.getPrefix();
                        suffix = ceQuery.getSuffix();
                        isInTarget = false;
                        boolean isAStr = NBAOperations.accepts(A, prefix, suffix);
                        if(isAStr) {
                            System.out.println("Inclusion violated... ");
                            System.out.println("prefix: " + parser.translate(prefix));
                            System.out.println("loop: " + parser.translate(suffix));
                            isEq = true;
                        }
                    }else {
                        // by rabit
                        t = timer.getCurrentTime();    
                        FiniteAutomaton rBFC = UtilInclusion.toRABITNBA(BFC);
                        boolean isIncluded = RABIT.isIncluded(rBFC, rB);
                        t = timer.getCurrentTime() - t;
                        this.timeBFCLessB += t;
                        String prefixStr = RABIT.getPrefix();
                        String suffixStr = RABIT.getSuffix();
                        if(isIncluded) {
                            options.log.println("Inclusion holds ...");
                            isEq = true;
                        }else {
                            isInTarget = false;
                            // check whether it is in A
                            prefix = alphabet.getWordFromString(prefixStr);
                            suffix = alphabet.getWordFromString(suffixStr);
                            boolean isAStr = NBAOperations.accepts(A, prefix, suffix);
                            
                            if(isAStr) {
                                System.out.println("Inclusion violated... ");
                                System.out.println("prefix: " + parser.translate(prefix));
                                System.out.println("loop: " + parser.translate(suffix));
                                isEq = true;
                            }
                        }
                    }
                }
            }
            
        }
        System.out.println("Done for checking equivalence...");
        Query<HashableValue> query = null;
        isInTarget = ! isInTarget;
        
        if(isEq) {
            query = new QuerySimple<>(alphabet.getEmptyWord(), alphabet.getEmptyWord());
            query.answerQuery(new HashableValueBoolean(true));
        }else {
            query = new QuerySimple<>(prefix, suffix);
            query.answerQuery(new HashableValueBoolean(false));
        }
        
        timer.stop();
        options.stats.timeOfEquivalenceQuery += timer.getTimeElapsed();
        ++ options.stats.numOfEquivalenceQuery;
        options.stats.timeOfLastEquivalenceQuery = timer.getTimeElapsed();
        
        if(options.verbose) System.out.println("counter example = " + query);
        return query;
    }
    
    public void print() {
        options.log.println("#B(F)/\\B = " + numInterBandBF);
        options.log.println("t#B(F)/\\B = " + timeInterBandBF + " ms");
        options.log.println("#A/\\B(F) = " + numInterAandBF);
        options.log.println("t#A/\\B(F) = " + timeInterAandBF + " ms");
        options.log.println("#B(F^c)/\\B(F) = " + numInterBFCandBF);
        options.log.println("t#B(F^c)/\\B(F) = " + timeInterBFCandBF + " ms");
        options.log.println("#!A<B = " + numBFCLessB );
        options.log.println("#t!A<B = " + timeBFCLessB + " ms");
    }

}
