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
import java.util.*;

final class Univ2 extends BuiltIn
{
    public final boolean unify(final Hashtable varsTbl)
    {
        final PrologObject func  = getRealTerm(getParam(1));
        final PrologObject param = getRealTerm(getParam(2));

        PrologObject term   = null;
        PrologObject list   = null;

        if (func == null)
        {
            if(param == null)
                throw new JIPInstantiationException(2);

            if(!(param instanceof List))
                throw new JIPTypeException(JIPTypeException.LIST, param);

            if(param.unifiable(List.NIL))
                throw new JIPDomainException("non_empty_list", param);

            try
            {
                term = getParam(1);

                PrologObject head = getRealTerm(((ConsCell)param).getHead());

                if(head instanceof Expression)
                {
                    PrologObject params = ((ConsCell)param).getTail();
                    if(params != null)  // come si comporta quintus
                    {
                        params = getRealTerm(params);
                        if(params != null && params != List.NIL && params != ConsCell.NIL)
                        	throw new JIPTypeException(JIPTypeException.ATOM, head);
                    }

                    list = head;
                }
                else if (head instanceof Atom)
                {
                    PrologObject params = ((ConsCell)param).getTail();
                    if(params == null)
                    {
                        list = head;
                    }
                    else
                    {
                        params = getRealTerm(params);
                      	
                        if(params == null || ((ConsCell)params).isPartial())
                        {
                            // caso X =.. [a|Y].
							throw new JIPInstantiationException(2);
                        }

                        int nArity = ((List)params).getHeight();
                        if(nArity == 0)
                        {
                            list = head;
                        }
                        else
                        {
                            ConsCell funparms = ((List)params).getConsCell();

//                          System.out.println("funparams " + funparms);
                            final String strName = ((Atom)head).getName() + "/" + Integer.toString(nArity);
                            final Atom name = Atom.createAtom(strName);
                            if(nArity == 2 && ((Atom)head).getName().equals("."))
                            {
                                // '.'/2 costruisce una lista, non un Functor,
                                // come fa gia' functor(T, '.', 2). Senza questo
                                // "X =.. ['.',a,[]]" dava il composto '.'(a,[]),
                                // che non e' ==/2 a [a]: il round-trip
                                // X =.. L, Y =.. L non tornava.
                                PrologObject tail = ((ConsCell)funparms.getTail()).getHead();
                                list = new List(funparms.getHead(), tail);
                            }
                            else if(BuiltInFactory.isBuiltIn(strName))
                                list = new BuiltInPredicate(name, funparms);
                            else if(name.equals(Atom.COMMA))
                            	list = funparms;
                            else
                                list = new Functor(name, funparms);
                        }

//                      System.out.println(list);
//                      System.out.println(((Functor)list).getName());
                    }
                }
                else if (head == null)
                {
                    throw new JIPInstantiationException(2);
                }
			    else if (((List)param).getHeight() == 1)
			    {
			    	throw new JIPTypeException(JIPTypeException.ATOMIC, head);
			    }
			    else
			    	throw new JIPTypeException(JIPTypeException.ATOM, head);
            }
            catch(ClassCastException ex)
            {
                ex.printStackTrace();
                throw new JIPTypeException(JIPTypeException.LIST, param);
            }
        }
        else
        {
            if(func instanceof Functor)
                term = new List(Atom.createAtom(((Functor)func).getFriendlyName()), new List(((Functor)func).getParams()));
            else if(func instanceof ConsCell && !(func instanceof List))
                term = new List(Atom.createAtom(","), new List((ConsCell)func));
            else if(func instanceof List && !func.unifiable(List.NIL))
            {
                // Una lista non vuota e' il composto '.'(Testa, Coda), come
                // gia' dice functor/3 (che su [a] risponde '.'/2). Qui cadeva
                // nel ramo atomico qui sotto, e "[a] =.. L" dava L = [[a]]:
                // =../2 e functor/3 si contraddicevano.
                final ConsCell cell = (ConsCell)func;

                // la coda dell'ultima cella e' null internamente, non List.NIL
                PrologObject tail = cell.getTail();
                if(tail == null)
                    tail = List.NIL;

                term = new List(Atom.createAtom("."),
                        new List(cell.getHead(), new List(tail, null)));
            }
            else
                term = new List(func, null);

            list = getParam(2);
        }

        return term.unify(list, varsTbl);
    }
}
