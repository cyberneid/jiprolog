% Il cut interno di JIProlog: '$!'/0 e '$!!'/1.
%
% Non sono ISO e non sono documentati per l'utente, ma non sono nemmeno un
% dettaglio privato: il kernel ci costruisce sopra ->/2 e *->/2
%
%   ->(X,Y)  :- call(X), !, '$!!'(Y).
%   *->(X,Y) :- call(X), '$!'/0, Y.
%
% e Disjunction2 li inietta nel corpo per l'if-then-else. Quindi se uno dei due
% cambia comportamento, a rompersi e' l'if-then-else, e il messaggio d'errore
% non nominera' mai '$!!'. I casi qui sotto li fissano direttamente, cosi' una
% regressione si vede dove nasce.
%
% La proprieta' che li distingue da call/1 e' questa: call/1 rende il cut
% opaco, '$!!'/1 no. Serve perche' il "then" di un if-then-else deve poter
% tagliare la clausola che lo contiene.

:- multifile(iso/3).

ic_p(1).
ic_p(2).
ic_p(3).

% cut dentro '$!!'/1: trasparente, taglia ic_transp/1
ic_transp(X) :- ic_p(X), '$!!'(!).

% lo stesso con call/1: opaco, non taglia niente
ic_opaque(X) :- ic_p(X), call(!).

% '$!'/0 nel corpo di una clausola normale non ha un ramo da tagliare
ic_soft(X) :- ic_p(X), '$!'.

% --- '$!!'/1 si comporta come call/1 per le soluzioni ---------------------
iso(intcut, (findall(X, '$!!'(ic_p(X)), L), L == [1,2,3]),                success).
iso(intcut, (findall(X, '$!!'((ic_p(X), X > 1)), L), L == [2,3]),         success).
iso(intcut, ('$!!'(true)),                                                success).
iso(intcut, ('$!!'(fail)),                                                failure).
iso(intcut, (findall(X, '$!!'('$!!'(ic_p(X))), L), L == [1,2,3]),         success).

% --- ... ma il cut al suo interno e' trasparente, non opaco come call/1 ----
iso(intcut, (findall(X, ic_transp(X), L), L == [1]),                      success).
iso(intcut, (findall(X, ic_opaque(X), L), L == [1,2,3]),                  success).

% --- '$!!'/1 lascia passare le eccezioni ----------------------------------
iso(intcut, (catch('$!!'(throw(ball)), B, true), B == ball),              success).

% --- '$!'/0 succede e da solo non taglia -----------------------------------
iso(intcut, '$!',                                                         success).
iso(intcut, (findall(X, ic_soft(X), L), L == [1,2,3]),                    success).

% --- e questo e' cio' per cui esistono: ->/2 e *->/2 sopra di essi ---------
% il "then" di ->/2 taglia la clausola che lo contiene (passa per '$!!'/1)
iso('7.8.5', (findall(X, (ic_p(X), ( true -> ! ; true )), L), L == [1]),   success).
% la condizione di ->/2 e' committata alla prima soluzione
iso('7.8.5', (findall(X, ( ic_p(X) -> true ; X = none ), L), L == [1]),    success).
% *->/2 invece le da' tutte (passa per '$!'/0)
iso('7.8.5', (findall(X, ( ic_p(X) *-> true ; X = none ), L), L == [1,2,3]),  success).
% e se la condizione fallisce del tutto, entrambi prendono l'else
iso('7.8.5', (findall(X, ( fail -> true ; X = none ), L), L == [none]),    success).
iso('7.8.5', (findall(X, ( fail *-> true ; X = none ), L), L == [none]),   success).
iso('7.8.5', (findall(X, ( (ic_p(X), fail) *-> true ; X = none ), L), L == [none]),  success).
% il cut nella condizione di *->/2 resta locale alla condizione
iso('7.8.5', (findall(X, ( (ic_p(X), !) *-> true ; X = none ), L), L == [1]),  success).
% il "then" di *->/2 taglia la clausola che lo contiene
iso('7.8.5', (findall(X, ( ic_p(X) *-> ! ; true ), L), L == [1]),          success).
% *->/2 annidato
iso('7.8.5', (findall(X-Y, ( ic_p(X) *-> ( ic_p(Y) *-> true ; Y = no ) ; X-Y = none-none ), L),
              length(L, 9)),                                              success).
