/*
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
 */

package com.ugos.jiprolog.engine;

/**
 * Le variabili legate durante un tentativo di unificazione, nell'ordine in cui
 * sono state legate.
 *
 * Qui c'era una Hashtable, allocata a ogni tentativo di match fra il goal e la
 * testa di una clausola: _unify ci metteva le variabili, e chi chiamava le
 * rileggeva per confermarle o per annullarle. Non serviva una tabella. Una
 * variabile finisce nel trail solo quando viene legata, e una volta legata
 * _unify segue il legame invece di rifarlo, quindi non ci sono duplicati da
 * togliere: bastano un array e un indice, senza hash e senza i lock che
 * Hashtable prende a ogni put.
 */
final class VariableTrail
{
    private Variable[] m_vars;
    private int        m_nSize;

    VariableTrail()
    {
        m_vars = new Variable[16];
    }

    final void add(final Variable var)
    {
        if(m_nSize == m_vars.length)
        {
            final Variable[] grown = new Variable[m_nSize * 2];
            System.arraycopy(m_vars, 0, grown, 0, m_nSize);
            m_vars = grown;
        }

        m_vars[m_nSize++] = var;
    }

    final int size()
    {
        return m_nSize;
    }

    final Variable get(final int nIndex)
    {
        return m_vars[nIndex];
    }

    /** Annulla i legami: e' il percorso del fallimento. */
    final void undo()
    {
        for(int i = 0; i < m_nSize; i++)
            m_vars[i].clear();

        m_nSize = 0;
    }
}
