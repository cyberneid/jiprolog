% Goal qualificati per modulo: Modulo:Goal.
%
% I moduli non sono nella ISO - sono un'estensione, e in JIProlog il modulo
% corrente vive in uno stack nella WAM che ogni goal spinge e il backtracking
% deve riportare indietro. E' proprio il punto in cui uno stack sbagliato non
% da' un errore ma una risposta sbagliata o un rallentamento silenzioso, quindi
% i casi qui sotto guardano le soluzioni, non lo stack.
%
% Le clausole sono asserite a runtime invece che messe in un file con
% :- module/2, cosi' il caso resta autocontenuto.

:- multifile(iso/3).

:- assert(mod_x:mx(1)).
:- assert(mod_x:mx(2)).
:- assert(mod_x:mx(3)).
:- assert(mod_y:my(a)).
:- assert(mod_y:my(b)).
:- assert((mod_x:mx_cut(X) :- mod_x:mx(X), !)).
:- assert((mod_x:mx_pair(X-Y) :- mod_x:mx(X), mod_x:mx(Y))).
:- assert((mod_x:mx_over(X) :- mod_x:mx(X), X > 1)).

mod_p(1).
mod_p(2).
mod_p(3).

% --- backtracking dentro un goal qualificato -------------------------------
iso(mod, (findall(X, mod_x:mx(X), L), L == [1,2,3]),                       success).
iso(mod, (mod_x:mx(2)),                                                    success).
iso(mod, (mod_x:mx(9)),                                                    failure).

% --- qualificato e non qualificato mescolati -------------------------------
iso(mod, (findall(X-Y, (mod_p(X), mod_x:mx(Y)), L), length(L, 9)),         success).
iso(mod, (findall(X-Y, (mod_x:mx(X), mod_p(Y)), L), length(L, 9)),         success).

% --- il cut dentro una clausola di modulo e' locale a quella clausola ------
iso(mod, (findall(X, mod_x:mx_cut(X), L), L == [1]),                       success).

% --- il cut fuori, dopo un goal qualificato, taglia il goal esterno --------
iso(mod, (findall(X, (mod_x:mx(X), !), L), L == [1]),                      success).
iso(mod, (findall(X, (mod_p(X), mod_x:mx(_), !), L), L == [1]),            success).

% --- goal qualificati annidati --------------------------------------------
iso(mod, (findall(P, mod_x:mx_pair(P), L), length(L, 9)),                  success).
iso(mod, (findall(X, mod_x:mx_over(X), L), L == [2,3]),                    success).

% --- fallimento dentro il modulo e ritorno fuori ---------------------------
iso(mod, (findall(X, (mod_p(X), \+ mod_x:mx(9)), L), L == [1,2,3]),        success).
iso(mod, (\+ mod_x:mx_over(0)),                                            success).

% --- goal qualificati dentro le costruzioni di controllo -------------------
iso(mod, (findall(X, (mod_x:mx(X) -> true ; X = none), L), L == [1]),      success).
iso(mod, (findall(X, (mod_x:mx(X) *-> true ; X = none), L), L == [1,2,3]), success).
iso(mod, (catch(mod_x:mx(_), _, fail)),                                    success).
iso(mod, (findall(X, (mod_x:mx(X) ; mod_y:my(X)), L), L == [1,2,3,a,b]),   success).

% --- una lunga catena di goal qualificati non perde soluzioni --------------
iso(mod, (findall(X, (mod_x:mx(_), mod_x:mx(_), mod_x:mx(X)), L), length(L, 27)),  success).

% --- il qualificatore deve sopravvivere al backtracking --------------------
% mod_x ha una sua my/1 con valori diversi da quella di mod_y: se dopo il
% backtracking su mod_x:mx(X) il goal mod_y:my(Y) perde il qualificatore e
% viene cercato nel modulo del goal precedente, le risposte non calano - si
% sporcano, ed e' quello che succedeva.
:- assert(mod_x:my(decoy1)).
:- assert(mod_x:my(decoy2)).

iso(mod, (findall(X-Y, (mod_x:mx(X), mod_y:my(Y)), L),
          L == [1-a, 1-b, 2-a, 2-b, 3-a, 3-b]),                            success).
iso(mod, (findall(Y, (mod_x:mx(_), mod_y:my(Y)), L),
          L == [a, b, a, b, a, b]),                                        success).
iso(mod, (findall(Y, mod_x:my(Y), L), L == [decoy1, decoy2]),              success).
