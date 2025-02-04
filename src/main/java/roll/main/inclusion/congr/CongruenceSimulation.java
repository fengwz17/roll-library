/* Copyright (c) since 2016                                               */
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

package roll.main.inclusion.congr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.TreeSet;

import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import roll.automata.NBA;
import roll.automata.StateNFA;
import roll.automata.operations.NBALasso;
import roll.automata.operations.NBAOperations;
import roll.automata.operations.StateContainer;
import roll.automata.operations.TarjanSCCsNonrecursive;
import roll.automata.operations.nba.inclusion.NBAInclusionCheckTool;
import roll.main.Options;
import roll.main.complement.IsIncluded;
import roll.main.inclusion.run.SuccessorInfo;
import roll.parser.ba.PairParserBA;
import roll.util.Pair;
import roll.util.Timer;
import roll.util.sets.ISet;
import roll.util.sets.UtilISet;
import roll.words.Word;

// This algorithm is inspired by simulation relation and the work 
//	Congruence Relations for B\"uchi Automata submitted to ICALP'21
// We actually can define congruence relations for language inclusion checking

public class CongruenceSimulation implements IsIncluded {
	
	NBA A;
	NBA B;
	
	// Simulation relation between A and B based on the congruence relations defined for B
	/**
	 * for each u, i_A - u -> q, i_B - u -> q', then we have q' simulates q for prefix u
	 * Here we actually define congruence relations for states in B with respect to states in A
	 * 
	 * That is, for prefix, we have (s_A, Set_B_states) for an equivalence class [u]
	 * if s_A = i_A, then Set_B_states = {i_B}
	 * otherwise, for each state s_A in A, if i_A - u -> s_A, then there must exist a state t in Set_B_states,
	 *  such that i_B - u -> t.
	 *  
	 *  Assume that for a state q in A that can reach an accepting state f on u' and
	 *  currently we encode q as [S1, S2] such that S1 \subseteq S2 over states of B,
	 *  If S1 - u -> [], it means that for some word uu' from i_A to f where u corresponds to S1,
	 *  no states in B can simulate the word uu' from the initial state i_B. 
	 *  Therefore, for the state q, we can just remove S2 and only represent q with [S1].
	 *  
	 *  Similarly, we can do the same to period computation
	 * */
	ArrayList<HashSet<ISet>> prefSim;
	StateContainer[] bStates;
	StateContainer[] aStates;
	
	/**
	 * for each v and final state q, then we have q' simulates q for period in B as follows:
	 * 
	 * q_A - v - > q'_A in A, then we need to have a path q_B - v -> q'_B
	 * q_A = v => q'_A in A (visiting accepting states), then we have q_B = v => q'_B
	 *
	 * */
	// only care about reachable states from q_A
	TIntObjectMap<HashSet<TreeSet<IntBoolTriple>>> periodSim;
	
	boolean antichain = true;
	boolean debug = false;
	
	// counterexample (prefix, period) if exists
	Word prefix = null;
	Word period = null;
	
	// p - a -> q cannot be simulated with transitions in B
//	int aPState = -1;
	int aQState = -1;
	int aLetter = -1;
	// I_A -> p or f -> p
	Word cePrefix = null;
	
	boolean computeCounterexample = true;
	
	HashMap<Pair<Integer, ISet>, Word> prefWordMap;
	
	HashMap<Pair<Integer, TreeSet<IntBoolTriple>>, Word> periodWordMap;
	
	boolean fwSim[][]; // simulation inside B 
	boolean useSimulation = false;
	boolean fwSimAB[][];
		
	public CongruenceSimulation(NBA A, NBA B) {
		this.A = A;
		this.B = B;
		prefSim = new ArrayList<>();
		for(int s = 0; s < A.getStateSize(); s ++)
		{
			prefSim.add(new HashSet<>());
		}
		aStates = new StateContainer[A.getStateSize()];
		// compute the predecessors and successors
		for(int i = 0; i < A.getStateSize(); i ++) {
          aStates[i] = new StateContainer(i, A);
		}
		// initialize the information for B
		for (int i = 0; i < A.getStateSize(); i++) {
			StateNFA st = aStates[i].getState();
			for (int letter = 0; letter < A.getAlphabetSize(); letter++) {
				for (int succ : st.getSuccessors(letter)) {
					//aStates[i].addSuccessors(letter, succ);
					aStates[succ].addPredecessors(letter, i);
				}
			}
		}
		bStates = new StateContainer[B.getStateSize()];
		// compute the predecessors and successors
		for(int i = 0; i < B.getStateSize(); i ++) {
          bStates[i] = new StateContainer(i, B);
		}
		// initialize the information for B
		for (int i = 0; i < B.getStateSize(); i++) {
			StateNFA st = bStates[i].getState();
			for (int letter = 0; letter < B.getAlphabetSize(); letter++) {
				for (int succ : st.getSuccessors(letter)) {
//					bStates[i].addSuccessors(letter, succ);
					bStates[succ].addPredecessors(letter, i);
				}
			}
		}
		periodSim = new TIntObjectHashMap<>();
		if(useSimulation) {
			fwSim = Simulation.computeForwardSimilation(B, bStates);
			fwSimAB = Simulation.computeForwardSimulation(A, B);
		}else {
			fwSim = new boolean[B.getStateSize()][B.getStateSize()];
			for(int s = 0; s < B.getStateSize(); s++) {
				for(int t = 0; t < B.getStateSize(); t ++) {
					fwSim[s][t] = (s == t);
				}
			}
			int num = A.getStateSize() + B.getStateSize();
			fwSimAB = new boolean[num][num];
			for(int s = 0; s < num; s++) {
				for(int t = 0; t < num; t ++) {
					fwSimAB[s][t] = (s == t);
				}
			}
		}
	}
	
	@Override
	public Pair<Word, Word> getCounterexample() {
		assert prefix != null && period != null;
		return new Pair<>(prefix, period);
	}
	
	public void outputPrefixSimulation() {
		for(int s = 0; s < A.getStateSize(); s ++)
		{
			// only i_B simulates i_A at first
			System.out.print("State " + s + "\n");
			for(ISet set : prefSim.get(s)) {
				if(!set.isEmpty()) System.out.println(set + ", ");
			}
		}
	}
	
	boolean isSimulated(ISet set, ISet update) {
		for(int p : set) {
			boolean simulated = false;
			for(int q : update) {
				if(fwSim[p][q]) {
					simulated = true;
					break;
				}
			}
			if(! simulated) {
				return false;
			}
		}
		return true;
	}
			
	// 
	HashSet<ISet> addSetToPrefixAntichain(HashSet<ISet> orig, ISet update, HashSet<ISet> subsetOfUpdate, boolean[] changed) {
		boolean contained = false;
		HashSet<ISet> result = new HashSet<>();
		changed[0] = false;
		// a set corresponds to a class of finite prefixes to an accepting state in A
		for(ISet sts: orig) {
			if(update.subsetOf(sts)) {
				// ignore sets that subsume update
				if(computeCounterexample) {
					subsetOfUpdate.add(sts);
				}
				continue;
			}else if(sts.subsetOf(update) || isSimulated(sts, update)){ // 
				// updated should not be added into the hashset
				contained = true;
				result.add(sts);
			}else {
				// update and sts are incomparable
				result.add(sts);
			}
		}
		if(! contained) {
			changed[0] = true;
			result.add(update);
		}
		return result;
	}
	
	/**
	 * Only compute the states that can reach accState
	 * */
	public void computePrefixSimulation(int accState, ISet reachSet) {
		if(computeCounterexample) {
			this.prefWordMap = new HashMap<>();
		}
		Timer timer = new Timer();
		timer.start();
		prefSim.clear();
		// initialization
		for(int s = 0; s < A.getStateSize(); s ++)
		{
			prefSim.add(new HashSet<>());
			// only i_B simulates i_A at first
			if(s == A.getInitialState()) {
				ISet set = UtilISet.newISet();
				set.set(B.getInitialState());
				prefSim.get(s).add(set);
				if(computeCounterexample) {
					this.prefWordMap.put(new Pair<>(s,  set), A.getAlphabet().getEmptyWord());
				}
			}
		}
		if(debug) System.out.println("Reachable size: " + reachSet.cardinality());
		// compute simulation relation
		LinkedList<Integer> workList = new LinkedList<>();
		workList.add(A.getInitialState());
		ISet inWorkList = UtilISet.newISet();
		inWorkList.set(A.getInitialState());
		while(! workList.isEmpty()) {
			// take out one state
			int s = workList.removeFirst();
			inWorkList.clear(s);
			// the letter is changed
			LinkedList<Pair<Integer, ISet>> removedPairs = new LinkedList<>();
			for(int a : A.getState(s).getEnabledLetters()) {
				for(int t : A.getState(s).getSuccessors(a)) {
					if(! reachSet.get(t)) continue;
					// s - a - > t in A
					// f(s) - a -> P'
					// p \in f(s), then P' \subseteq f(t) in B
					// compute mapping relations to B
					// a set corresponds to a word u
					HashSet<ISet> copy = new HashSet<>();
					for(ISet set : prefSim.get(s)) {
						copy.add(set);
					}
					for(ISet set : copy) {
						// for every set, we update the sets
						ISet update = UtilISet.newISet();
						boolean isFwSimulated = false;
						for (int p : set) {
							for (int q : B.getSuccessors(p, a)) {
								// update the states for t
								update.set(q);
							}
						}
						for(int q : update) {
							if(fwSimAB[t][q + A.getStateSize()]) {
								isFwSimulated = true;
								break;
							}
						}
						if(isFwSimulated) {
							System.out.println("Ignore state the set " + update + " for " + t);
							continue;
						}	
						// update is the word ua and check whether we need to update
						if (! prefSim.get(t).contains(update)) {
							//TODO: Antichain, only keep the set that are not a subset of another
							boolean changed = false;
							if(antichain) {
								HashSet<ISet> orig = prefSim.get(t);
								boolean[] modified = new boolean[1];
								HashSet<ISet> subsetsOfUpdate = new HashSet<>();
								HashSet<ISet> result = addSetToPrefixAntichain(orig, update, subsetsOfUpdate, modified);
								changed = modified[0];
								prefSim.set(t, result);
								if(changed && computeCounterexample) {
									Pair<Integer, ISet> pair = new Pair<>(s, set);
									Word pref = this.prefWordMap.get(pair);
									Word newPref = pref.append(a);
									this.prefWordMap.put(new Pair<>(t,  update), newPref);
									for(ISet subset : subsetsOfUpdate) {
										removedPairs.add(new Pair<>(t, subset));
									}
								}
							}else {
								prefSim.get(t).add(update);
								changed = true;
							}
							if(changed && !inWorkList.get(t)) {
								workList.addLast(t);
								inWorkList.set(t);
							}
						}
						// detected that update is empty for the first time
						// now need to update 
						if(update.isEmpty()) {
							aQState = t;
							aLetter = a;
							cePrefix = this.prefWordMap.get(new Pair<>(s, set));
							prefSim.get(accState).add(UtilISet.newISet());
							return;
						}
					}
				}
			}
			for(Pair<Integer, ISet> pair : removedPairs) {
				prefWordMap.remove(pair);
			}
		}
		timer.stop();
		this.timeForPrefixSim += timer.getTimeElapsed();
	}
	
	private ISet getPredSet(int state, StateContainer[] states, NBA nba) {
		LinkedList<Integer> queue = new LinkedList<>();
		ISet visited = UtilISet.newISet();
        queue.add(state);
        visited.set(state);
        boolean accLoop = false;
        while(! queue.isEmpty()) {
        	int lState = queue.remove();
            // ignore unused states
            for(int c = 0; c < nba.getAlphabetSize(); c ++) {
                for(StateNFA lPred : states[lState].getPredecessors(c)) {
                    if(! visited.get(lPred.getId())) {
                        queue.add(lPred.getId());
                        visited.set(lPred.getId());
                    }else if(lPred.getId() == state) {
                    	// the state can reach itself
//                    	if(debug) System.out.println("Found loop from state " + state);
                    	accLoop = true;
                    }
                }
            }
        }
        if(! accLoop) {
        	visited.clear(state);
        }
        return visited;
	}
	
	private ISet getReachSet(int state, NBA nba) {
		LinkedList<Integer> queue = new LinkedList<>();
        queue.add(state);
        ISet visited = UtilISet.newISet();
        visited.set(state);
        while(! queue.isEmpty()) {
        	int lState = queue.remove();
            // ignore unused states
            for(int c = 0; c < nba.getAlphabetSize(); c ++) {
                for(int lSucc : nba.getSuccessors(lState, c)) {
                    if(! visited.get(lSucc)) {
                        queue.add(lSucc);
                        visited.set(lSucc);
                    }
                }
            }
        }
        return visited;
	}
	
	// ignore the set that already contains one set in the sets
	boolean containTriples(HashSet<TreeSet<IntBoolTriple>> sets, TreeSet<IntBoolTriple> set) {
		for(TreeSet<IntBoolTriple> s : sets) {
			if(set.containsAll(s)) { //s.equals(set)
				return true;
			}
		}
		return false;
	}
	
	boolean isSimulated(TreeSet<IntBoolTriple> set, TreeSet<IntBoolTriple> update) {
		for(IntBoolTriple fstTriple : set) {
			boolean simulated = false;
			for(IntBoolTriple sndTriple : update) {
				if(fwSim[fstTriple.getLeft()][sndTriple.getLeft()] 
				&& fwSim[fstTriple.getRight()][sndTriple.getRight()]
				&& (!fstTriple.getBool() || sndTriple.getBool())) {
					simulated = true;
					break;
				}
			}
			if(! simulated) {
				return false;
			}
		}
		return true;
	}
	
	HashSet<TreeSet<IntBoolTriple>> addSetToPeriodAntichain(HashSet<TreeSet<IntBoolTriple>> orig, TreeSet<IntBoolTriple> update
			, HashSet<TreeSet<IntBoolTriple>> subsetOfUpdate, boolean[] changed) {
		HashSet<TreeSet<IntBoolTriple>> result = new HashSet<TreeSet<IntBoolTriple>>();
		boolean contained = false;
		for(TreeSet<IntBoolTriple> triples: orig) {
			if(triples.containsAll(update)) {
				// ignore sets that subsume update
				if(computeCounterexample) {
					subsetOfUpdate.add(triples);
				}
				continue;
			}else if(update.containsAll(triples) || isSimulated(triples, update)) { // 
				// must add triples
				contained = true;
				result.add(triples);
			}else {
				result.add(triples);
			}
		}
		if(!contained) {
			changed[0] = true;
			result.add(update);
		}
		return result;
	}
	
	private long timeForPrefixSim = 0;
	private long timeForPeriodSim = 0;
	
	
	
	// Always guarantte that if there is (q, r: true), then no (q, r: false) appears
	private void addTriple(TreeSet<IntBoolTriple> set, IntBoolTriple triple) {
		IntBoolTriple revTriple = new IntBoolTriple(triple.left, triple.right, !triple.acc);
		boolean containedRev = set.contains(revTriple);
		if(containedRev) {
			if(triple.acc) {
				set.remove(revTriple);
				set.add(triple);
			}else {
				// do nothing, keep the original one
			}
		}else {
			set.add(triple);
		}
	}
	
	// the Input simulatedStatesInB can simulate accState
	@SuppressWarnings("unchecked")
	public void computePeriodSimulation(int accState, ISet simulatedStatesInB, ISet bReachSet) {
		if(computeCounterexample) {
			this.periodWordMap = new HashMap<>();
		}
		Timer timer = new Timer();
		timer.start();
		periodSim.clear();
		// now compute every state that can be reached by accState
		ISet reachSet = getReachSet(accState, A);
		// only keep those state that can go back to accState
		ISet predSet = getPredSet(accState, aStates, A);
		reachSet.and(predSet);
		if(debug) System.out.println("States for A: " + reachSet);
		if(debug) System.out.println("States for B: " + simulatedStatesInB);
		// those can not be reached should corresponds to empty set
		for(int s : reachSet)
		{
			// only i_B simulates i_A at first
			periodSim.put(s, new HashSet<TreeSet<IntBoolTriple>>());
		}
		LinkedList<Integer> workList = new LinkedList<>();
		ISet inWorkList = UtilISet.newISet();
		System.out.println("Computing the congruence representation of periods for accepting state " + accState + " ...");
		// 1. initialization
		{
			// only care about states from simulatedStatesInB
			int s = accState;
			LinkedList<Pair<Integer, TreeSet<IntBoolTriple>>> removedPairs = new LinkedList<>();
			// v must not be empty word
			for (int a : A.getState(s).getEnabledLetters()) {
				for (int t : A.getSuccessors(s, a)) {
					if(!reachSet.get(t)) continue;
					// add to worklist
					workList.add(t);
					inWorkList.set(t);
					// compute the simulation relations
					TreeSet<IntBoolTriple> set = new TreeSet<>();
					// s - a -> t
					for (int p : simulatedStatesInB) {
						for (int q : B.getSuccessors(p, a)) {
//							if(!bReachSet.get(q)) continue;
							// put every p - a -> q in f(t)
							boolean acc = B.isFinal(p) || B.isFinal(q);
							addTriple(set, new IntBoolTriple(p, q, acc));
						}
					}
					boolean isFwSimulated = false;
					for(IntBoolTriple tr : set) {
						if(fwSimAB[t][tr.getRight() + A.getStateSize()]) {
							isFwSimulated = true;
							break;
						}
					}
					if(isFwSimulated) {
						System.out.println("Ignore state the set " + set + " for " + t);
						continue;
					}	
					if(debug) System.out.println(t + " AccTriple " + set);
					//TODO: Antichain, only keep the set that are a subset of another
					if(antichain ) { // && ! containTriples(periodSim.get(t), set)
						// keep subsets
						HashSet<TreeSet<IntBoolTriple>> curr = periodSim.get(t);
						boolean[] modified = new boolean[1];
						HashSet<TreeSet<IntBoolTriple>> subsetOfUpdate = new HashSet<>();
						HashSet<TreeSet<IntBoolTriple>> result = addSetToPeriodAntichain(curr, set, subsetOfUpdate, modified);
						periodSim.put(t, result);
						if(modified[0] && computeCounterexample) {
							Word pref = A.getAlphabet().getLetterWord(a);
							this.periodWordMap.put(new Pair<>(t,  set), pref);
							for(TreeSet<IntBoolTriple> key : subsetOfUpdate) {
								removedPairs.add(new Pair<>(t, key));
							}
						}
						if(modified[0] && t == accState) {
							// decide whether it ...
							for(ISet pref: this.prefSim.get(accState)) {
								if(! decideAcceptance(pref, set)) {
									System.out.println("Early Early terminated " + pref + " " + set);
									return ;
								}
							}
							if(debug) System.out.println("Deciding Done... ");
						}
						if(debug) System.out.println("AccTriple " + set);
					}else {
						periodSim.get(t).add(set);	
					}
					if(set.isEmpty()) {
						cePrefix = A.getAlphabet().getEmptyWord();
						aQState = t;
						aLetter = a;
						this.periodSim.get(accState).add(new TreeSet<>());
						return;
					}
//					System.out.println("t = " + t + " set = " + set);
//					System.out.println("word = " + this.periodWordMap.get(new Pair<>(t,  set)));
				}
			}
			for(Pair<Integer, TreeSet<IntBoolTriple>> pair : removedPairs) {
				this.periodWordMap.remove(pair);
			}
		}
		
		// 2. computation of simulated relations
		while(! workList.isEmpty()) {
			int s = workList.removeFirst();
			inWorkList.clear(s);
			LinkedList<Pair<Integer, TreeSet<IntBoolTriple>>> removedPairs = new LinkedList<>();
			// update for successors
			for(int a : A.getState(s).getEnabledLetters()) {
				for(int t : A.getSuccessors(s, a)) {
					// Again, ignore states that cannot reach accState
					if(!reachSet.get(t)) continue;
					// s - a -> t
					HashSet<TreeSet<IntBoolTriple>> copy = new HashSet<>();
					for(TreeSet<IntBoolTriple> set: periodSim.get(s)) {
						copy.add(set);
					}
					for(TreeSet<IntBoolTriple> set: copy) {
						TreeSet<IntBoolTriple> update = new TreeSet<>();
						// put sets
						for(IntBoolTriple triple : set) {
							int p = triple.getLeft();
							int q = triple.getRight();
							for(int qr : B.getSuccessors(q, a)) {
//								if(!bReachSet.get(q)) continue;
								boolean acc = B.isFinal(qr) || triple.getBool();
								IntBoolTriple newTriple  = new IntBoolTriple(p, qr, acc);
								addTriple(update, newTriple);
							}
						}
						boolean isFwSimulated = false;
						for(IntBoolTriple tr : update) {
							if(fwSimAB[t][tr.getRight() + A.getStateSize()]) {
								isFwSimulated = true;
								break;
							}
						}
						if(isFwSimulated) {
							System.out.println("Ignore state the set " + update + " for " + t);
							continue;
						}	
						// we have extended for set
						if(! containTriples(periodSim.get(t), update)) {
							//TODO: Antichain, only keep the set that are a subset of another
							boolean changed = false;
							if(antichain) {
								HashSet<TreeSet<IntBoolTriple>> curr = periodSim.get(t);
								boolean[] modified = new boolean[1];
								HashSet<TreeSet<IntBoolTriple>> subsetOfUpdate = new HashSet<>();
								HashSet<TreeSet<IntBoolTriple>> result = addSetToPeriodAntichain(curr, update, subsetOfUpdate, modified);
								changed = modified[0];
								periodSim.put(t, result);
								if(modified[0] && computeCounterexample) {
									Word pref = this.periodWordMap.get(new Pair<>(s, set));
									Word newPref = pref.append(a);
									this.periodWordMap.put(new Pair<>(t,  update), newPref);
									for(TreeSet<IntBoolTriple> key : subsetOfUpdate) {
										removedPairs.add(new Pair<>(t, key));
									}
								}
								if(modified[0] && t == accState) {
									// decide whether it ...
									for(ISet pref: this.prefSim.get(accState)) {
										if(! decideAcceptance(pref, update)) {
											System.out.println("Early terminated " + pref + " " + update);
											return ;
										}
									}
								}
//								if(debug && t == accState) {
//									System.out.println("Set = " + this.periodSim.get(t));
//								}
//								System.out.println("Loop AccTriple " + set + " state " + t + " from " + s + " modified = " + changed + " a = " + a + " Asize = " + A.getAlphabetSize());
							}else {
								changed = true;
								periodSim.get(t).add(update);
							}
							if(changed && !inWorkList.get(t)) {
								workList.add(t);
								inWorkList.set(t);
							}
						}
						// not possible
						if(update.isEmpty()) {
							aQState = t;
							cePrefix = this.periodWordMap.get(new Pair<>(s, set));
							aLetter = a;
							System.out.println("Early termination in computing representation of periods.");
							this.periodSim.get(accState).add(new TreeSet<>());
							return;
						}
					}
				}
			}
			for(Pair<Integer, TreeSet<IntBoolTriple>> pair : removedPairs) {
				this.periodWordMap.remove(pair);
			}
		}
		timer.stop();
		this.timeForPeriodSim += timer.getTimeElapsed();
//		System.out.println("Finished computing the congruence representation for accepting state " + accState + " ...");		
	}
	
	public boolean isEquvalent() {
		return false;
	}
	
	private boolean coveredBy(Pair<HashSet<ISet>, HashSet<TreeSet<IntBoolTriple>>> left
			, Pair<HashSet<ISet>, HashSet<TreeSet<IntBoolTriple>>> right) {
		// check whether left is coveredby right
		// first, check prefix
		for(ISet lSet : left.getLeft()) {
			boolean covered = false;
			for(ISet rSet : right.getLeft()) {
				if(rSet.subsetOf(lSet)) {
					covered = true;
					break;
				}
			}
			if(! covered) {
				return false;
			}
		}
		for(TreeSet<IntBoolTriple> lTriples : left.getRight()) {
			boolean covered = false;
			for(TreeSet<IntBoolTriple> rTriples : right.getRight()) {
				if(lTriples.containsAll(rTriples)) {
					covered = true;
					break;
				}
			}
			if(! covered) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public Boolean isIncluded() {
		if(fwSimAB[0][A.getStateSize()]) {
			return true;
		}
		// for each accepting state (should be reachable from the initial state and can reach itself)
		ISet reachSet = getReachSet(A.getInitialState(), A);
		int countStates = 0;
//		LinkedList<Pair<HashSet<ISet>, HashSet<TreeSet<IntBoolTriple>>>> antichainFinals = new LinkedList<>();
		long timeForAcceptance = 0;
		TIntObjectMap<HashSet<ISet>> finalsSim = new TIntObjectHashMap<>();
		for(int accState : A.getFinalStates()) {
			countStates ++;
			System.out.println("Checking for "+ countStates + "-th accepting state " + accState + " out of " + A.getFinalStates().cardinality() + " states");
			// reachable states from the initial state
			ISet necessaryStates = reachSet.clone();
			// only keep those state that can go back to accState
			ISet predSet = getPredSet(accState, aStates, A);
			necessaryStates.and(predSet);
			// if the initial state cannot reach the accepting state or the accepting state cannot reach itself
			//, then language is empty
			if(!necessaryStates.get(A.getInitialState()) || !necessaryStates.get(accState)) {
				//System.out.println("Ignored the accepting state " + accState + "");
				continue;
			}
			if(debug) System.out.println("Necessary states in A: " + necessaryStates + " #size = " + necessaryStates.cardinality());
			computePrefixSimulation(accState, necessaryStates);
			//outputPrefixSimulation();
			for(int i = 0; i < A.getStateSize() && debug; i ++) {
				System.out.println("state " + i + " -> " + prefSim.get(i));
			}
//			System.exit(-1);
			// obtain the necessary part for accState
			HashSet<ISet> prefSims = prefSim.get(accState);
			if(debug) System.out.println("Acc simulated sets: " + prefSims);
			if(prefSims.isEmpty()) {
				// any word that reaches accState will do
				assert false;
			}
			// only keep the sets that are subset of another
			HashSet<ISet> antichainPrefix = new HashSet<>();
			// compute antichain
			for(ISet sim1: prefSims) {
				boolean subsumes = false;
				for(ISet sim2: prefSims) {
					// ignore itself
					if(sim1 == sim2) continue;
					// only keep those that do not subsume others
					if(sim2.subsetOf(sim1)) {
						subsumes = true;
						break;
					}
				}
				if(! subsumes) {
					// not subsume others
					antichainPrefix.add(sim1);
				}
			}
			finalsSim.put(accState, antichainPrefix);
			ISet simulatedStatesInB = UtilISet.newISet();
			for(ISet sim: antichainPrefix) {
				if(sim.isEmpty()) {
					// empty means some word to accState cannot be simulated
//					System.out.println("Computing counterexample ...");
					if(computeCounterexample) computeCounterexamplePrefix(accState, necessaryStates);
//					System.out.println("Prefix: " + prefix);
//					System.out.println("Period: " + period);
					return false;
				}
				simulatedStatesInB.or(sim);
			}
			//simulatedStatesInB = getReachSet(simulatedStatesInB);
			if(debug) System.out.println("Prefix simulated sets: " + antichainPrefix);
			if(debug) System.out.println("Necessary states for B: " + simulatedStatesInB);
			// now we compute the simulation for periods from accState
//			System.out.println("pref rep: " + antichainPrefix + " -> " + simulatedStatesInB);
//			TarjanSCCs sccs = new TarjanSCCs(B, simulatedStatesInB);
			ISet allowSccs = UtilISet.newISet();
			ISet bFinals = B.getFinalStates();			
//			System.out.println("Final states: " + bFinals);
//			for(ISet scc : sccs.getSCCs()) {
//				System.out.println("SCC: " + scc);
//				if(scc.overlap(simulatedStatesInB) && scc.overlap(bFinals)) {
//					allowSccs.or(scc);
//				}
//			}
//			System.out.println("Nonrecursive: ");
//			TarjanSCCsNonrecursive sccNonrecur = new TarjanSCCsNonrecursive(B, simulatedStatesInB);;
//			for(ISet scc : sccNonrecur.getSCCs()) {
//				System.out.println("SCC: " + scc);
//				if(scc.overlap(simulatedStatesInB) && scc.overlap(bFinals)) {
//					System.out.println("SCC 2: " + scc);
//				}
//			}
//			System.out.println("pre scc: " + simulatedStatesInB);
//			System.out.println("allow scc: " + allowSccs);
//			simulatedStatesInB.and(allowSccs);
//			if(debug) System.out.println("simulated states in B: " + simulatedStatesInB);
//			System.out.println("Final states: " + B.getFinalStates());
			computePeriodSimulation(accState, simulatedStatesInB, allowSccs);
			// now decide whether there is one word accepted by A but not B
			System.out.println("Deciding the language inclusion between L(A^i_f) (A^f_f)^w and L(B) ...");
			Timer timer = new Timer();
			timer.start();
			for(ISet pref: antichainPrefix) {
				if(debug) System.out.println("Simulated set in B: " + pref);
				//				computePeriodSimulation(accState, pref);
				// compute antichain
				HashSet<TreeSet<IntBoolTriple>> antichainPeriod = new HashSet<>();
				for(TreeSet<IntBoolTriple> period1: periodSim.get(accState)) {
					boolean subsumes = false;
					for(TreeSet<IntBoolTriple> period2: periodSim.get(accState)) {
						if(period1 == period2) continue;
						if(period1.containsAll(period2)) {
							subsumes = true;
							break;
						}
					}
					if(!subsumes) {
						antichainPeriod.add(period1);
					}
				}
				if(antichainPeriod.contains(new TreeSet<>())) {
					// empty means some word from accState to itself cannot be simulated
					computeCounterexamplePeriod(accState, pref, necessaryStates);
					return false;
				}
				for(TreeSet<IntBoolTriple> period: antichainPeriod) {
					if(debug) System.out.println("Simulated triples in B: " + period);
					// decide whether this pref (period) is accepting in B
					if(! decideAcceptance(pref, period)) {
						// we need to construct a counterexample here
						if(computeCounterexample) computeCounterexample(accState, pref, period, necessaryStates);
						return false;
					}
				}
			}
			timer.stop();
			timeForAcceptance += timer.getTimeElapsed();
//			antichainFinals.add(new Pair(prefSim.get(accState), periodSim.get(accState)));
		}
		System.out.println("Time for deciding acceptance: " + timeForAcceptance);
		System.out.println("Time for computing prefix simulation: " + this.timeForPrefixSim);
		System.out.println("Time for computing period simulation: " + this.timeForPeriodSim);

		return true;
	}
	
	// This function will be called if decideAcceptance returns false
	private void computeCounterexample(int accState, ISet pref, TreeSet<IntBoolTriple> period, ISet aReachSet) {
		// first, compute the word to this pref
//		System.out.println("Pref: " + pref);
//		System.out.println("Period: " + period);
		// construct prefix
		this.prefix = this.prefWordMap.get(new Pair<>(accState, pref));
		this.period = this.periodWordMap.get(new Pair<>(accState, period));
//		System.out.println("pref = " + prefix + ", period = " + this.period);
	}

	// goal must be reachable from start
	private Word computeWordInA(int start, int goal) {
		PriorityQueue<SuccessorInfo<Integer>> queue = new PriorityQueue<SuccessorInfo<Integer>>();
		SuccessorInfo<Integer> info = new SuccessorInfo<Integer>(start);
		info.word = A.getAlphabet().getEmptyWord();
		queue.add(info);
		ISet visited = UtilISet.newISet();
		visited.set(start);
		
		while(! queue.isEmpty()) {
			SuccessorInfo<Integer> curr = queue.remove();
            // trace back to the initial state
            for(int letter : A.getState(curr.state).getEnabledLetters()) {
            	// for sure current state has empty set
            	Word word = curr.word.append(letter);
                for(int succId : A.getState(curr.state).getSuccessors(letter)) {
                	// now add those states into it
                	if(! visited.get(succId)) {
                		SuccessorInfo<Integer> succInfo = new SuccessorInfo<Integer>(succId);
                		succInfo.word = word;
                		queue.add(succInfo);
                		visited.set(succId);
                	}
                	if(succId == goal) {
                		return word;
                	}
                }
            }
		}
		assert false;
		return null;
	}
	
	private void computeCounterexamplePrefix(int accState, ISet reachSet) {
		assert (aQState >= 0);
		System.out.println("Computing counterexample for the accepting state " + accState);
		//System.out.println("Computing counterexample prefix: " + accState + " bSetForP: " + bSetForP);
		ISet initSet = UtilISet.newISet();
		initSet.set(B.getInitialState());
		// construct prefix
//		 Word p1 = computeWordInProduct(initSet, bSetForP, reachSet);
		Word p1 = this.cePrefix;
		this.prefix = p1.append(this.aLetter);
		Word p2 = computeWordInA(aQState, accState);
		this.prefix = this.prefix.concat(p2);
		
		// construct loop
		this.period = A.getAlphabet().getEmptyWord();
        int start = -1;
        for(int letter : A.getState(accState).getEnabledLetters()) {
        	// just choose this one
        	for(int succ : A.getState(accState).getSuccessors(letter)) {
        		if(reachSet.get(succ)) {
        			this.period = this.period.append(letter);
//        			System.out.println("Start = " + succ + " letter = " + letter);
        			start = succ;
        			break;
        		}
        	}
        	if(start >= 0) {
        		break;
        	}
        }
//        System.out.println("Start = " + start + " accState = " + accState);
        Word suffix = computeWordInA(start, accState);
//        System.out.println("pref = " + this.period + "suffix = " + suffix);
        assert(suffix != null);
        this.period = this.period.concat(suffix);
	}
	
	// This function can be called when SCC decomposition has been performed
	private void computeCounterexamplePeriod(int accState, ISet pref, ISet reachSet) {
		assert (aQState >= 0);
//		System.out.println("Computing counterexample period: " + accState + " reachSet: " + reachSet);
		// first the prefix
		this.prefix = this.prefWordMap.get(new Pair<>(accState, pref));
		// construct period
		Word p2 = computeWordInA(aQState, accState);
		this.period = cePrefix.append(this.aLetter);
		this.period = this.period.concat(p2);
	}
//
	private static TreeSet<IntBoolTriple> compose(TreeSet<IntBoolTriple> first
			, TreeSet<IntBoolTriple> second) { //, ISet sndStates
		TreeSet<IntBoolTriple> result = new TreeSet<>();
		for(IntBoolTriple fstTriple: first) {
			for(IntBoolTriple sndTriple: second) {
				// (p, q, ) \times (q, r) -> (p, r)
				if(fstTriple.getRight() == sndTriple.getLeft()) {
					result.add(new IntBoolTriple(fstTriple.getLeft()
							, sndTriple.getRight()
							, fstTriple.getBool() || sndTriple.getBool()));
				}
			}
		}
		return result;
	}
	
	private TreeSet<IntBoolTriple> extend(TreeSet<IntBoolTriple> first, int letter) {
		TreeSet<IntBoolTriple> result = new TreeSet<>();
		for(IntBoolTriple triple: first) {
			int state = triple.getRight();
			for(int succ: B.getState(state).getSuccessors(letter)) {
				addTriple(result, new IntBoolTriple(triple.getLeft()
							, succ
							, triple.getBool() || B.isFinal(succ)));
			}
		}
		return result;
	}
	
	private TreeSet<IntBoolTriple> compose(ISet preds, TreeSet<IntBoolTriple> triples) {
		TreeSet<IntBoolTriple> result = new TreeSet<>();
		for(IntBoolTriple triple: triples) {
			if(preds.get(triple.getLeft())) {
				addTriple(result, triple);
			}
		}
		return result;
	}
	
	// decide whether there exists an accepting run in B from states in sim
	// all states on the left are from pref
	private static boolean decideAcceptance(ISet pref, TreeSet<IntBoolTriple> period) {
//		System.out.println("pref: " + pref);
//		System.out.println("period: " + period);
//		System.out.println("Start deciding acceptance ...");
		boolean foundLoop = false;
		ISet reachStates = pref.clone();
//		for(int state: pref) {
			// iteratively check whether there exists a triple (q, q: true) reachable from state
		ISet reachableStates = pref.clone();
		TreeSet<IntBoolTriple> reachSet = new TreeSet<>();
		while (true) {
			ISet newReach = UtilISet.newISet();
			for (IntBoolTriple triple : period) {
				if (reachStates.get(triple.getLeft())) {
					reachSet.add(triple);
					// add states that can be reached
					newReach.set(triple.getRight());
				}
			}
//			System.out.println("ReachSet = " + reachSet);
			// first add reachable triples
			// compute update
			int origSize = reachSet.size();
			TreeSet<IntBoolTriple> update = compose(reachSet, period);
			// reachable states from pref can also be first states
			reachSet.addAll(update);
//			System.out.println("ReachSet = " + reachSet);
			// a triple (q, q: true) means that we have found an accepting run
			for (IntBoolTriple triple : reachSet) {
				if (triple.getLeft() == triple.getRight() && triple.getBool()) {
					foundLoop = true;
					break;
				}
			}
			for(IntBoolTriple triple : update) {
				// more reachable states 
				newReach.set(triple.getRight());
			}
			// new reachable states
//			System.out.println("New reach = " + newReach);
			int statesSize = reachableStates.cardinality();
			newReach.andNot(reachableStates);
			reachableStates.or(newReach);
			// reach a fixed point
			if (foundLoop || (origSize == reachSet.size() && statesSize == reachableStates.cardinality())) {
					break;
			}
			reachStates = newReach;
		}
//		}
//		System.out.println("Finished deciding acceptance ...");

		return foundLoop;
	}

	public static void main(String[] args) {
		
//		TreeSet<IntBoolTriple> set1 = new TreeSet<>();
//		set1.add(new IntBoolTriple(0, 0, true));
//		set1.add(new IntBoolTriple(24, 0, true));
//		
//		TreeSet<IntBoolTriple> set2 = new TreeSet<>();
//		set2.add(new IntBoolTriple(0, 0, true));
//		
//		System.out.println(set1.containsAll(set1));
//		Alphabet alphabet = new Alphabet();
//		alphabet.addLetter('a');
//		alphabet.addLetter('b');
//		NBA A = new NBA(alphabet);
//		A.createState();
//		A.createState();
//		A.getState(0).addTransition(0, 0);
//		A.getState(0).addTransition(1, 1);
//		A.getState(1).addTransition(1, 1);
//		A.setFinal(1);
//		A.setInitial(0);
//		
//		NBA B = new NBA(alphabet);
//		B.createState();
//		B.createState();
////		B.createState();
////		B.getState(0).addTransition(0, 0);
//		B.getState(0).addTransition(1, 1);
////		B.getState(0).addTransition(0, 2);
//		B.getState(1).addTransition(1, 1);
//		B.getState(1).addTransition(0, 1);
////		B.getState(2).addTransition(0, 2);
////		B.getState(2).addTransition(1, 2);
//
//		B.setFinal(1);
//		B.setInitial(0);
		
//		System.out.println(args.length + " " + args[0]);
		ISet pref = UtilISet.newISet();
		pref.set(0);
		pref.set(1);
		pref.set(2);
		TreeSet<IntBoolTriple> set1 = new TreeSet<>();
		set1.add(new IntBoolTriple(0, 0, false));
		set1.add(new IntBoolTriple(0, 108, false));
		set1.add(new IntBoolTriple(0, 109, true));
		set1.add(new IntBoolTriple(0, 110, true));
		set1.add(new IntBoolTriple(0, 112, false));
		set1.add(new IntBoolTriple(0, 120, false));
		set1.add(new IntBoolTriple(1, 1, false));

		set1.add(new IntBoolTriple(1, 2, true));
		set1.add(new IntBoolTriple(1, 112, true));
		set1.add(new IntBoolTriple(2, 112, true));

		set1.add(new IntBoolTriple(109, 109, true));
		set1.add(new IntBoolTriple(109, 110, true));
		set1.add(new IntBoolTriple(110, 109, true));
		set1.add(new IntBoolTriple(110, 110, true));
		set1.add(new IntBoolTriple(112, 112, false));
		
		System.out.print(decideAcceptance(pref, set1));



//		System.exit(0);


		
		
		Options options = new Options();
		PairParserBA pairParser = new PairParserBA(options, args[0], args[1]);
		NBA A = pairParser.getA();
		NBA B = pairParser.getB();
		System.out.println("#A = " + A.getStateSize() + ", #B = " + B.getStateSize());
		Timer timer = new Timer();
		timer.start();
		
		CongruenceSimulation sim = new CongruenceSimulation(A, B);
		sim.antichain = true;
		sim.computeCounterexample = true;
		sim.debug = false;
		sim.useSimulation = true;
		boolean included = sim.isIncluded();
		System.out.println(included ? "Included" : "Not included");
		if(!included && sim.computeCounterexample) {
			NBALasso lasso = new NBALasso(sim.prefix, sim.period);
			pairParser.print(lasso.getNBA(), options.log.getOutputStream());
			boolean inA = NBAOperations.accepts(A, sim.prefix, sim.period);
			boolean inB = NBAOperations.accepts(B, sim.prefix, sim.period);
			if(!(inA && !inB)) {
				System.out.println("Error counterexample: " + inA + ", " + inB);
			}
			inA = NBAOperations.tarjanAccepts(A, sim.prefix, sim.period);
			inB = NBAOperations.tarjanAccepts(B, sim.prefix, sim.period);
			if(!(inA && !inB)) {
				System.out.println("Error counterexample: " + inA + ", " + inB);
			}else {
				System.out.println("True counterexample: " + inA + ", " + inB);
			}
			//System.out.println("SPOT: " + NBAInclusionCheckTool.isIncludedSpot(lasso.getNBA(), A));
			//System.out.println("SPOT" + NBAInclusionCheckTool.isIncludedSpot(lasso.getNBA(), B));
			//System.out.println("RABIT: " + NBAInclusionCheckTool.isIncludedGoal("/home/liyong/tools/GOAL-20151018/goal", lasso.getNBA(), A));
			//System.out.println("RABIT" + NBAInclusionCheckTool.isIncludedGoal("/home/liyong/tools/GOAL-20151018/goal", lasso.getNBA(), B));
		}
		timer.stop();
		System.out.println("Time elapsed " + timer.getTimeElapsed());
		
	}
	
}