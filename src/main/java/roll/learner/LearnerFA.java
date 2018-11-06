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

package roll.learner;

import roll.main.Options;
import roll.oracle.MembershipOracle;
import roll.query.Query;
import roll.query.QuerySimple;
import roll.table.ExprValue;
import roll.table.HashableValue;
import roll.table.ObservationRow;
import roll.words.Alphabet;
import roll.words.Word;

/**
 * @author Yong Li (liyong@ios.ac.cn)
 * 
 * Learner for the finite automata accepting finite words
 * 
 * */

public abstract class LearnerFA<M> extends LearnerBase2<M>{

    public LearnerFA(Options options, Alphabet alphabet, MembershipOracle<HashableValue> membershipOracle) {
        super(options, alphabet, membershipOracle);
    }
    
    @Override
    protected Query<HashableValue> makeMembershipQuery(ObservationRow row, int offset, ExprValue exprValue) {
        return new QuerySimple<>(row, row.getWord(), exprValue.get(), offset);
    }

    @Override
    protected ExprValue getInitialColumnExprValue() {
        return getExprValueWord(alphabet.getEmptyWord());
    }

    @Override
    protected Query<HashableValue> makeMembershipQuery(Word prefix, ExprValue exprValue) {
        throw new UnsupportedOperationException("LearnerFA does not support makeMembershipQuery(Word prefix, ExprValue exprValue)");
    }


}
