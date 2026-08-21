% ISO/IEC 13211-1 sections 7.8.9 (catch/3) e 7.8.10 (throw/1), piu' il punto
% dove il cut e le eccezioni si incontrano.
%
% catch/3 chiama Goal come lo chiamerebbe call/1, quindi il cut dentro Goal e'
% opaco verso l'esterno; e' ri-soddisfacibile, quindi non e' un once/1
% mascherato; e la palla e' una copia, quindi Recovery non puo' vedere i legami
% che Goal aveva fatto. Ognuna di queste tre e' facile da sbagliare in modi che
% i test sul cut da solo non vedono.

:- multifile(iso/3).

exc_p(1).
exc_p(2).
exc_p(3).

% lancia solo oltre la soglia: serve per far fallire il backtracking a meta'
exc_over(N) :- ( N > 2 -> throw(too_big(N)) ; true ).

% throw da una clausola gia' committata: il cut non trattiene la palla
exc_after_cut(X) :- exc_p(X), !, throw(boom).

% cut nel Recovery
exc_recovery_cut(L) :- findall(X, catch(throw(t), _, (exc_p(X), !)), L).

% --- il cut dentro Goal e' locale a catch/3 -------------------------------
iso('7.8.9', (findall(X, (exc_p(X), catch((!, true), _, true)), L), L == [1,2,3]),  success).
iso('7.8.9', (findall(X, catch((exc_p(X), !), _, true), L), L == [1]),              success).
iso('7.8.9', (findall(X, (exc_p(X), catch(catch(!, _, true), _, true)), L), L == [1,2,3]),  success).

% --- catch/3 e' ri-soddisfacibile -----------------------------------------
iso('7.8.9', (findall(X, catch(exc_p(X), _, fail), L), L == [1,2,3]),               success).
iso('7.8.9', (catch(true, _, fail)),                                                success).
iso('7.8.9', (catch(fail, _, true)),                                                failure).
iso('7.8.9', ((catch(exc_p(_), _, true), fail)),                                    failure).

% --- una palla lanciata dopo un cut risale comunque ------------------------
iso('7.8.9', (catch(exc_after_cut(_), B, true), B == boom),                         success).

% --- il Recovery e' chiamato come call/1: il cut e' locale -----------------
iso('7.8.9', (exc_recovery_cut(L), L == [1]),                                       success).

% --- la palla e' una copia: Recovery non vede i legami di Goal -------------
iso('7.8.9', (catch(throw(f(_)), f(Y), var(Y))),                                    success).
iso('7.8.9', (catch((Z = bound, throw(ball)), _, var(Z))),                          success).

% --- un catch che non unifica lascia passare la palla ----------------------
iso('7.8.9', (catch(catch(throw(inner), other, true), B, true), B == inner),        success).
iso('7.8.9', (catch(throw(inner), other, true)),                                    ball(inner)).

% --- l'eccezione interrompe il backtracking a meta' ------------------------
iso('7.8.9', (findall(N, (exc_p(N), catch(exc_over(N), _, fail)), L), L == [1,2]),  success).
iso('7.8.9', (catch((exc_p(_), throw(mid)), M, true), M == mid),                    success).
iso('7.8.9', (catch(findall(X, (exc_p(X), exc_over(X)), _), B, true), B == too_big(3)),  success).

% --- throw/1 su una variabile e' un errore di istanziazione ----------------
iso('7.8.10', (catch(throw(_), error(E, _), true), E == instantiation_error),       success).
iso('7.8.10', throw(_),                                                             error(instantiation_error)).

% --- gli errori dei built-in sono error/2 e sono intercettabili ------------
iso('7.8.9', (catch(_ is 1 + foo, error(E, _), true), E == type_error(evaluable, foo/0)),  success).
iso('7.8.9', (catch(atom_length(_, _), error(E, _), true), E == instantiation_error),      success).

% --- catch e' trasparente a ->, che resta locale alla sua condizione -------
iso('7.8.9', (findall(X, (exc_p(X), catch(( ! -> true ; true ), _, true)), L), L == [1,2,3]),  success).
