/*****************************************
 * 27/03/2003
 *
 * Copyright (C) 1999-2003 Ugo Chirico
 * http://www.ugochirico.com
 *
 * This is free software; you can redistribute it and/or
 * modify it under the terms of the Affero GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Affero GNU General Public License for more details.
 *
 * You should have received a copy of the Affero GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *****************************************/

package com.ugos.jiprolog.extensions.terms;

import com.ugos.jiprolog.engine.*;

import java.util.*;

public class NumberCodes2 extends JIPXCall
{
    public final boolean unify(final JIPCons input, Hashtable<JIPVariable, JIPVariable> varsTbl)
    {
        JIPTerm number = input.getNth(1);
        JIPTerm codes = input.getNth(2);

        if(number instanceof JIPVariable && ((JIPVariable)number).isBounded())
            number = ((JIPVariable)number).getValue();

        if(codes instanceof JIPVariable && ((JIPVariable)codes).isBounded())
            codes = ((JIPVariable)codes).getValue();

        // ISO 8.16.4.2: quando la lista c'e' tutta e' lei a decidere - si
        // analizza e il numero si unifica col risultato. Rendere il numero nel
        // proprio testo canonico e' l'altra direzione, e vale solo quando la
        // lista non c'e' ancora.
        //
        // Erano invertite: col numero legato si generava sempre il canonico e
        // lo si confrontava, quindi number_codes(3.3, "3.3") riusciva
        // e lo stesso numero scritto "3.3E+0" falliva.
        final String strText = NumberText.textOf(codes, false);

        if(strText != null)
            return number.unify(NumberText.parse(strText), varsTbl);

        if(number instanceof JIPNumber)
            return NumberText.render((JIPNumber)number, false).unify(codes, varsTbl);

        if(number instanceof JIPVariable)
            throw new JIPInstantiationException(2);

        throw new JIPTypeException(JIPTypeException.NUMBER, number);
    }

    public boolean hasMoreChoicePoints()
    {
        return false;
    }
}

