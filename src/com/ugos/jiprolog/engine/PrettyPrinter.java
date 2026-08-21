/*
 * 23/04/2014
 *
 * Copyright (C) 1999-2014 Ugo Chirico
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
 */

package com.ugos.jiprolog.engine;

import java.util.Hashtable;
import com.ugos.util.ValueEncoder;

final class PrettyPrinter extends Object
{
    private static final char[] ESCAPE = {'a', 'b', 't', 'n', 'v', 'f', 'r'};

    // ISO 7.10.5. Ogni operando si scrive con una priorita' massima: se il
    // funtore principale del sottotermine e' un operatore di priorita'
    // superiore, il sottotermine va fra parentesi, altrimenti rileggendo il
    // testo si ottiene un termine diverso da quello scritto.
    //
    // Il limite di partenza e' 1200 (un termine intero); un argomento di
    // termine composto e un elemento di lista si scrivono a 999, sotto la
    // priorita' della virgola, cosi' f(a, (b,c), d) non diventa f(a,b,c,d).
    private static final int MAX_PRIORITY = 1200;
    private static final int ARG_PRIORITY = 999;
    private static final int COMMA_PRIORITY = 1000;
    private static String Q_CHARS = "\\()[].,`{}\"";

    public static final String printTerm(final PrologObject obj, final OperatorManager opManager, final boolean bQ)
    {
    	Hashtable<String, Variable> varTable = new Hashtable<String, Variable>();
    	return print(obj, opManager, bQ, varTable);
    }

    private static final String print(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable)
    {
    	StringBuilder sb = new StringBuilder();
    	print(obj, opManager, bQ, varTable, sb);

        return sb.toString();
    }

    private static final void print(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb)
    {
        print(obj, opManager, bQ, varTable, sb, MAX_PRIORITY);
    }

    // nMaxPrec e' la priorita' massima consentita in questa posizione.
    private static final void print(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb, final int nMaxPrec)
    {
        if (obj instanceof Atom)
        {
            printAtom(obj, opManager, bQ, sb, nMaxPrec);
        }
        else if(obj instanceof Functor)
        {
            printFunctor(obj, opManager,bQ, varTable, sb, nMaxPrec);
        }
        else if(obj instanceof Clause)
        {
            printClause(obj, opManager, bQ, varTable, sb);
        }
        else if(obj instanceof Expression)
        {
            printExpression(obj, bQ, sb);
        }
//        else if(obj instanceof PString)
//        {
//            return printPString((PString)obj);
//        }
        else if(obj instanceof List)
        {
            printList(obj, opManager, bQ, varTable, sb);
        }
        else if(obj instanceof ConsCell)
        {
            // Una congiunzione non e' un Functor ','/2: e' una ConsCell nuda,
            // quindi la parentesi va decisa qui e non in printOperator.
            final boolean bBracket = opManager != null
                    && nMaxPrec < COMMA_PRIORITY
                    && ((ConsCell)obj).getTail() != null
                    && ((ConsCell)obj).getHead() != null;

            if(bBracket)
                sb.append('(');

            printCons(obj, opManager,bQ, varTable, sb);

            if(bBracket)
                sb.append(')');
        }
        else if(obj instanceof Variable)
        {
            printVariable(obj, opManager, bQ, varTable, sb, nMaxPrec);
        }
        else if(obj == null)
        {
        	sb.append("");
        }
        else
        {
            throw new JIPRuntimeException("Invalid type cannot be printed");//return "";
        }
    }

    private static final void printAtom(final PrologObject obj, final OperatorManager opManager, final boolean bQ, StringBuilder sb, final int nMaxPrec)
    {
        final String strAtom = ((Atom)obj).getName();

        // ISO 7.10.5: un atomo che e' anche un operatore, usato come operando,
        // vale quanto l'operatore, e va fra parentesi se la posizione non
        // arriva a tanto. Senza, EOS = not si rilegge come EOS = (not ; ...)
        // perche' il parser prende not come prefisso - e infatti xio.pl scrive
        // gia' a mano EOS = (not).
        if(opManager != null && operatorPriority(strAtom, opManager) > nMaxPrec)
        {
            sb.append('(');
            printAtomString(strAtom, opManager, bQ, sb);
            sb.append(')');
            return;
        }

        printAtomString(strAtom, opManager,bQ, sb);
    }

    // La priorita' di un atomo-operatore: la piu' alta fra le forme
    // dichiarate, che e' quella con cui il parser potrebbe leggerlo.
    private static final int operatorPriority(final String strAtom, final OperatorManager opManager)
    {
        if(!opManager.contains(strAtom))
            return 0;

        final Operator op = opManager.get(strAtom);

        int nPrec = op.getPrecedence();

        final Operator supp = op.getSupplementaryOp();
        if(supp != null && supp.getPrecedence() > nPrec)
            nPrec = supp.getPrecedence();

        return nPrec;
    }

    private static final void printAtomString(final String strAtom, final OperatorManager opManager, final boolean bQ, StringBuilder sb)
    {
        if(opManager == null || bQ)  // canonical
        {
            if(strAtom.length() == 0)
            {
            	sb.append("''");
            	return;
            }
            
            if(strAtom.equals("{}"))
            {
            	sb.append("{}");
            	return;
            }

            if(strAtom.equals("[]"))
            {
            	sb.append("[]");
            	return;
            }
            
            if(strAtom.equals("%"))
            {
            	sb.append("'%'");
            	return;
            }

            if(strAtom.equals("/*"))
            {
            	sb.append("'/*'");
            	return;
            }

            StringBuilder sbAtom = new StringBuilder();

            boolean bQuoted = false;

            if(Q_CHARS.indexOf(strAtom) > -1)
                bQuoted = true;

            // se comincia con una maiuscola occorre l'apice
            if(PrologTokenizer.UPPERCASE_CHARS.indexOf(strAtom.charAt(0)) > -1)
                bQuoted = true;

            // se comincia con un numero occorre l'apice
            char c = strAtom.charAt(0);
            if(c < 58 && c > 47)
                bQuoted = true;

            // se contiene almeno uno spazio occorre l'apice
            if(strAtom.indexOf(' ') > -1)
                bQuoted = true;

            boolean bSimpleFound = false;
            boolean bSpecialFound = false;
            boolean bSingletonFound = false;

            // tratta i caratteri speciali
            for(int i = 0; i < strAtom.length(); i++)
            {
                switch(strAtom.charAt(i))
                {
                    case '\'':
                    	sbAtom.append("\\'");
                        bQuoted = true;
                        break;

                    case '\\':
                        bSpecialFound = true;
                        sbAtom.append("\\\\");
                        bQuoted = true;
                        break;

                    default:
                        c = strAtom.charAt(i);
                        if(PrologTokenizer.LOWERCASE_CHARS.indexOf(c) > -1 ||
                           PrologTokenizer.UPPERCASE_CHARS.indexOf(c) > -1 ||
                           (c < 58 && c > 47))
                        {
                            bSimpleFound = true;
                            sbAtom.append(c);
                        }
                        else if(PrologTokenizer.SINGLETON_CHARS.indexOf(c) > -1)
                        {
                            bSingletonFound = true;
                            sbAtom.append(c);
                        }
                        else if(c <= 13 && c >= 7)
                        {
                            bQuoted = true;
                            sbAtom.append('\\').append(ESCAPE[c - 7]);
                        }
                        else if(c < ' ')
                        {
                            bQuoted = true;
                            sbAtom.append("\\x").append(ValueEncoder.byteToHexString((byte)c));
                            //strRet+= "~" + (char)(c + '@');
                        }
                        else
                        {
                            bSpecialFound = true;
                            sbAtom.append(c);
                        }
                }
            }

            bQuoted = bQuoted ||
                    (bSimpleFound && bSingletonFound) ||
                    (bSpecialFound && bSingletonFound) ||
                    (bSimpleFound && bSpecialFound);

            if(bQuoted)
            {
            	sb.append('\'').append(sbAtom).append('\'');

//                return "'" + strRet + "'";
            }
            else
            {
                sb.append(sbAtom);
            }
        }
        else
        {
        	sb.append(strAtom);
//            return strAtom;
        }
    }

    private static final void printFunctor(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb, final int nMaxPrec)
    {
        final Functor funct = (Functor)obj;

        // Estrae il nome
        final String strFunctor = funct.getFriendlyName();

        if(opManager != null && funct.getAtom().getName().equals("$VAR/1"))
        {
            // Estrae i parametri
        	String val = print(funct.getParams().getTerm(1), opManager, bQ, varTable);
        	try
        	{
        		int n = Integer.parseInt(val);
        		int r = n % 26;
        		int d = n / 26;

        		char c = (char)('A' + r);

        		sb.append(c).append(((d > 0) ? d : ""));

        		return;
        	}
        	catch(NumberFormatException ex)
        	{

        	}
        }

        if(opManager != null && opManager.contains(strFunctor))
        {
            final Operator op = opManager.get(strFunctor);
            if((funct.getArity() == 1 && (op.getPrefix() != null || op.getPostfix() != null)) ||
                   (funct.getArity() == 2 && (op.getInfix() != null)))
            {
                printOperator(obj, opManager, bQ, varTable, sb, nMaxPrec);
                return;
            }
        }


        ConsCell params = funct.getParams();

        // Costruisce la stringa
        if(params == null)
        {
            printAtomString(strFunctor, opManager, bQ, sb);
        }
        else
        {
            // tratta il caso speciale {}
            // ISO 7.10.5: '{}'(T) si scrive {T}. Le graffe isolano, quindi
            // dentro si riparte da 1200.
            if(opManager != null && strFunctor.equals("{}") && funct.getArity() == 1)
            {
                sb.append('{');
                print(params.getHead(), opManager, bQ, varTable, sb, MAX_PRIORITY);
                sb.append('}');
                return;
            }

            printAtomString(strFunctor, opManager, bQ, sb);

            sb.append('(');

            printParams(params, opManager, bQ, varTable, sb);

            sb.append(')');
        }
    }

    private static final void printOperator(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb, final int nMaxPrec)
    {
        final Functor oper = (Functor)obj;

        // Estrae il nome
        String strOper = oper.getFriendlyName();

        // Estrae i parametri
        final ConsCell params = oper.getParams();
        if(params == null)
        {
            printAtomString(strOper, opManager, bQ, sb);
            return;
        }

        int nArity = params.getHeight();

        final Operator op = opManager.get(strOper);

        PrologObject head = params.getHead();

        // Quale dei tre operatori omonimi si sta stampando: e' l'arita' a
        // deciderlo, non l'ordine in cui sono stati dichiarati.
        final Operator opUsed;
        if(nArity == 1 && op.getPrefix() != null)
            opUsed = op.getPrefix();
        else if(nArity == 1 && op.getPostfix() != null)
            opUsed = op.getPostfix();
        else
            opUsed = (op.getInfix() != null) ? op.getInfix() : op;

        final int nPrec = opUsed.getPrecedence();

        // ISO 7.10.5: se l'operatore lega piu' debolmente di quanto la
        // posizione consenta, tutto il termine va fra parentesi - e dentro le
        // parentesi il limite riparte da 1200.
        final boolean bBracket = nPrec > nMaxPrec;
        final int nLeft;
        final int nRight;

        if(bBracket)
        {
            sb.append('(');
        }

        // yfx: l'operando dalla parte della y accetta la stessa priorita',
        // quello dalla parte della x una in meno. Cosi' a-b-c resta -(-(a,b),c)
        // e a-(b-c) prende le parentesi.
        nLeft  = opUsed.isLeftAssoc()  ? nPrec : nPrec - 1;
        nRight = opUsed.isRightAssoc() ? nPrec : nPrec - 1;

        if (nArity == 1 && op.getPrefix() != null)
        {
            printAtomString(strOper, opManager, bQ, sb);
            sb.append(' ');
            print(head, opManager,bQ, varTable, sb, nRight);
        }
        else if (nArity == 1 && op.getPostfix() != null)
        {
        	print(head, opManager,bQ, varTable, sb, nLeft);
        	sb.append(' ');
        	printAtomString(strOper, opManager, bQ, sb);
        }
        else //if (op.isInfix())
        {
            PrologObject tail = BuiltIn.getRealTerm(params.getTail());

        	print(head, opManager,bQ, varTable, sb, nLeft);  // first

        	sb.append(' ');

        	printAtomString(strOper, opManager, bQ, sb);

        	sb.append(' ');

            //System.out.println(tail.getClass());
            if(tail instanceof List || tail instanceof Functor || !(tail instanceof ConsCell))
            {
                print(tail, opManager, bQ, varTable, sb, nRight); // second
            }
            else
            {
                print(((ConsCell)tail).getHead(), opManager, bQ, varTable, sb, nRight); // second;
            }
        }

        if(bBracket)
        {
            sb.append(')');
        }
    }

    private static final void printClause(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb)
    {
        // Estrae il nome
        final PrologObject head = ((Clause)obj).getHead();
        final ConsCell tail = (ConsCell)((Clause)obj).getTail();

        // :- e' 1200 xfx, quindi entrambi gli operandi stanno a 1199.
        print(head, opManager, bQ, varTable, sb, MAX_PRIORITY - 1);
        if(tail != null)
        {
            sb.append(":-");
            print(tail.getHead(), opManager, bQ, varTable, sb, MAX_PRIORITY - 1);  // body
        }

        sb.append('.');
    }

    private static final void printExpression(final PrologObject obj, final boolean bQ, StringBuilder sb)
    {
        final double dVal = ((Expression)obj).getValue();

        // long e non int: gli interi arrivano fino a 2^53 (Expression.MAX_INTEGER)
        // e un cast a int li stamperebbe saturati a 2147483647
        if(((Expression)obj).isInteger())
            sb.append(Long.toString((long)dVal));
        else
        	sb.append(Double.toString(dVal));
    }

    private static final void printList(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb)
    {
        if(opManager == null) // canonical
        {
            if(((List)obj).isNil())
            {
            	sb.append("[]");
            }
            else if(((List)obj).getTail() != null)
            {
            	sb.append("'.'(");
            	print(((List)obj).getHead(), null, bQ, varTable, sb);
            	sb.append(",");
            	print(((List)obj).getTail(), null, bQ, varTable, sb);
            	sb.append(')');
            }
            else
            {
            	sb.append("'.'(");
                print(((List)obj).getHead(), null, bQ, varTable, sb);
                sb.append(",[])");
            }
        }
        else
        {
        	sb.append('[');
        	printCons(obj, opManager, bQ, varTable, sb);
        	sb.append(']');
        }
    }
//    private static final String printPString(final PString string)
//    {
//    	String double_quotes = string.getDoubleQuotes();
//    	String val = string.getString();
//
//    	if(double_quotes.equals("chars"))
//    	{
//    		char vals[] = new char[val.length()];
//
//    		val.getChars(0, val.length(), vals, 0);
//
//    		String ret = "[";
//    		for(int i = 0; i < vals.length - 1; i++)
//    		{
//
//    			ret += vals[i] + ",";
//    		}
//
//    		ret += vals[vals.length - 1] + "]";
//
//    		return ret;
//    	}
//    	else if(double_quotes.equals("atom"))
//    	{
//    		return val;
//    	}
//    	else
//    	{
//    		char vals[] = new char[val.length()];
//
//    		val.getChars(0, val.length(), vals, 0);
//
//    		String ret = "[";
//    		for(int i = 0; i < vals.length - 1; i++)
//    		{
//
//    			ret += (int)vals[i] + ",";
//    		}
//
//    		ret += (int)vals[vals.length - 1] + "]";
//
//    		return ret;
//    	}
//
//    }
    private static final void printParams(final ConsCell cons, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb)
    {
        // Stampa di una lista
        PrologObject term = cons;
        PrologObject head = null;

        if(cons.getHead() == null)
        {
            return;
        }

        while (term != null)
        {
            head = ((ConsCell)term).getHead();

            if(head != null)
            {
            	print(head, opManager, bQ, varTable, sb, ARG_PRIORITY);

                term = ((ConsCell)term).getTail();

                if(term != null)
                {
                    head = ((ConsCell)term).getHead();

                    if(head != null)
                    	sb.append(',');
                }
            }
            else
            {
                term = null;
            }
        }
    }
//    private static final String printConsCell(final PrologObject obj)
//    {
//        final ConsCell cons = (ConsCell)obj;
//        final PrologObject obj1 = BuiltIn.getRealTerm(cons.getTail());
//        if(obj1 != null && obj1 != ConsCell.NIL)
//            return "("  + printCons(obj) + ")";
//        else
//            return printCons(obj);
//    }

    private static final void printCons(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb)
    {
        // Stampa di una lista
        final ConsCell cons = (ConsCell)obj;
        PrologObject term = cons;
        PrologObject head = null;

        if(cons.getHead() == null)
        {
            return;
        }
        else if(opManager != null)
        {
            while (term != null)
            {
                head = ((ConsCell)term).getHead();

                if(head != null)
                {
                    if(head instanceof Variable && ((Variable)head).isBounded())
                        head = BuiltIn.getRealTerm(head);

//                    System.out.println(head.getClass());
                    // Elemento di lista, oppure congiunto di una congiunzione:
                    // in entrambi i casi la virgola che segue lo delimita, e
                    // 999 e' il limite. print() mette le parentesi da se',
                    // congiunzioni comprese.
                    print(head, opManager, bQ, varTable, sb, ARG_PRIORITY);

                    term = ((ConsCell)term).getTail();

                    if(term instanceof Variable)
                    {
                        Variable var = (Variable)term;
                        term = BuiltIn.getRealTerm(term);

                        if(term == null)
                        {
                        	sb.append('|');
                        	print(var, opManager, bQ, varTable, sb, ARG_PRIORITY);
                        }
                    }

                    if(term instanceof ConsCell)
                    {
                        head = ((ConsCell)term).getHead();

                        if(head instanceof Variable && ((Variable)head).isBounded())
                            head = BuiltIn.getRealTerm(head);

                        if(head != null)
                        	sb.append(',');
                    }
                    else if(term != null)
                    {
                    	sb.append('|');
                    	print(term, opManager, bQ, varTable, sb, ARG_PRIORITY);
                        term = null;
                    }
                }
                else
                {
                    term = null;
                }
            }
        }
        else
        {
            // Stampa di una lista canonica

        	StringBuilder sbHead = new StringBuilder();

            head = ((ConsCell)term).getHead();
            if(head instanceof Variable && ((Variable)head).isBounded())
                head = BuiltIn.getRealTerm(head);

            term = ((ConsCell)term).getTail();
            if(term instanceof Variable && ((Variable)term).isBounded())
                term = BuiltIn.getRealTerm(term);

            if(head != null)
            {
            	print(head, opManager, bQ, varTable, sbHead);// + ", ";
            }

            if(term instanceof ConsCell)
            {
                head = ((ConsCell)term).getHead();

                if(head instanceof Variable && ((Variable)head).isBounded())
                    head = BuiltIn.getRealTerm(head);

                if(head != null)
                {
                	sb.append("','(");
                	sb.append(sbHead);
                	sb.append(',');
                	print(term, opManager, bQ, varTable, sb);
                	sb.append(')');
                }
                else
                {
                    sb.append(sbHead);
                }
            }
            else if(term != null)
            {
            	sb.append("','(");
            	sb.append(sbHead);
            	sb.append(',');
            	print(term, opManager, bQ, varTable, sb);
            	sb.append(')');
            }
            else
            {
                sb.append(sbHead);
            }
        }
    }

    private static final void printVariable(final PrologObject obj, final OperatorManager opManager, final boolean bQ, Hashtable<String, Variable> varTable, StringBuilder sb, final int nMaxPrec)
    {
        // Stampa di una variabile
        final Variable var = ((Variable)obj).lastVariable();

        final PrologObject object = var.getObject();

        if(var.cyclic() && varTable.containsKey(var.getName()))
//        if(varTable.containsKey(var.getName()))
        {
        	sb.append(var.getName());
        	return;
        }
        else if(object == null)
        {
        	sb.append('_').append(Long.toString(var.getAddress()));// + ":" + Integer.toString(var.hashCode()); /* + var.getName() + "?";*/
        	return;
            //return var.getName()  + " = " + "_" + Integer.toString(var.getAddress());// + ":" + Integer.toString(var.hashCode()); /* + var.getName() + "?";*/
        }
        else
            varTable.put(var.getName(), var);

        print(object, opManager, bQ, varTable, sb, nMaxPrec);
    }
}

