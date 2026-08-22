/*****************************************
 * Copyright (C) 1999-2018 Ugo Chirico
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

/**
 * Il testo di un numero, nelle due direzioni, condiviso da number_chars/2 e
 * number_codes/2.
 *
 * <p>Stava scritto due volte, una per classe, e il difetto era in entrambe.
 */
final class NumberText
{
    private NumberText()
    {
    }

    /**
     * Il testo che la lista rappresenta, oppure null se non c'e' niente da
     * leggere: coda aperta, elemento non ancora legato, o proprio un altro
     * tipo di termine. In quel caso tocca all'altra direzione.
     */
    static String textOf(final JIPTerm term, final boolean bChars)
    {
        if(term instanceof JIPString)
            return ((JIPString)term).getStringValue();

        if(!(term instanceof JIPList) || ((JIPList)term).isPartial())
            return null;

        try
        {
            return JIPString.create((JIPList)term, bChars).getStringValue();
        }
        catch(JIPInstantiationException notReadyYet)
        {
            // un elemento della lista e' ancora una variabile: la lista non
            // dice quale numero sia, quindi non e' lei a decidere
            return null;
        }
    }

    /**
     * Il numero che il testo denota, secondo la sintassi dei token numerici
     * della ISO 6.4.4 e 6.4.5, con l'eventuale segno meno che la 8.16.4.1
     * ammette davanti.
     *
     * <p>Il ramo decimale usava Double.parseDouble, che accetta la sintassi
     * dei letterali Java: 3d dava 3, 3.3f dava 3.3, Infinity dava 2147483647,
     * NaN dava 0 e 1e5 dava 100000, e nessuno di questi e' un numero Prolog.
     * Passava inosservato perche' con il numero legato non ci si arrivava mai
     * - vedi textOf sopra - e con il numero libero nessuno scrive 3d.
     *
     * <p>Le basi (0x, 0o, 0b, 0'c) restano trattate qui e non delegate al
     * lettore dei termini: JIPTermParser.parseTerm non le riconosce, legge
     * "0xf" come 0.
     */
    static JIPTerm parse(final String strText)
    {
        // layout iniziale ammesso, finale no
        final String strTrimmed = strText.replaceAll("^\\s+", "");

        if(strTrimmed.length() == 0
                || strTrimmed.length() != strTrimmed.replaceAll("\\s+$", "").length())
            throw new JIPSyntaxErrorException("not_a_number");

        final boolean bNegative = strTrimmed.charAt(0) == '-';
        final String strToken = bNegative ? strTrimmed.substring(1) : strTrimmed;

        if(strToken.length() == 0)
            throw new JIPSyntaxErrorException("not_a_number");

        final JIPNumber number = parseToken(strToken);

        if(!bNegative)
            return number;

        return number.isInteger()
                ? JIPNumber.create(-(long)number.getDoubleValue())
                : JIPNumber.create(-number.getDoubleValue());
    }

    private static JIPNumber parseToken(final String strToken)
    {
        try
        {
            // 0'c - il codice del carattere che segue
            if(strToken.startsWith("0'") && strToken.length() > 2)
                return JIPNumber.create(strToken.codePointAt(2));

            if(strToken.startsWith("0x"))
                return JIPNumber.create(Long.parseLong(digits(strToken, HEX), 16));

            if(strToken.startsWith("0o"))
                return JIPNumber.create(Long.parseLong(digits(strToken, OCTAL), 8));

            if(strToken.startsWith("0b"))
                return JIPNumber.create(Long.parseLong(digits(strToken, BINARY), 2));

            if(strToken.matches(INTEGER))
                return JIPNumber.create(Long.parseLong(strToken));

            if(strToken.matches(FLOAT))
                return JIPNumber.create(Double.parseDouble(strToken));
        }
        catch(NumberFormatException tooBigOrMalformed)
        {
            throw new JIPSyntaxErrorException("not_a_number");
        }

        throw new JIPSyntaxErrorException("not_a_number");
    }

    // ISO 6.4.4: un intero decimale e' una sequenza di cifre; un float vuole
    // almeno una cifra da entrambi i lati del punto, e l'esponente da solo non
    // basta a farne un float - "1e5" non e' un numero.
    private static final String INTEGER = "[0-9]+";
    private static final String FLOAT   = "[0-9]+\\.[0-9]+([eE][-+]?[0-9]+)?";

    private static final String HEX    = "[0-9a-fA-F]+";
    private static final String OCTAL  = "[0-7]+";
    private static final String BINARY = "[01]+";

    /** Le cifre dopo il prefisso di base, o un errore se non sono valide. */
    private static String digits(final String strToken, final String strPattern)
    {
        final String strDigits = strToken.substring(2);

        if(!strDigits.matches(strPattern))
            throw new JIPSyntaxErrorException("not_a_number");

        return strDigits;
    }

    /** Il testo canonico del numero, come lista di caratteri o di codici. */
    static JIPString render(final JIPNumber number, final boolean bChars)
    {
        final String strNumber;

        if(number.isInteger())
            strNumber = Long.toString((long)number.getDoubleValue());
        else
            strNumber = Double.toString(number.getDoubleValue());

        return JIPString.create(strNumber, bChars);
    }
}
