% ISO/IEC 13211-1 sezione 7.8.3 - la metachiamata: una variabile usata come
% goal, ossia call/1 scritto senza scriverlo.
%
% Il punto delicato non e' la prima chiamata ma la seconda. Il goal di un nodo
% viene normalizzato prima di risolverlo - la variabile diventa il termine a
% cui e' legata - e se quella normalizzazione viene scritta nella lista di
% goal condivisa col padre, al backtracking il padre ci ritrova il termine
% della soluzione precedente invece della variabile. Il risultato non e' un
% errore: e' la prima soluzione rieseguita all'infinito, con la variabile del
% template che intanto avanza. (g(G), G) con g(t1), g(t2), g(t3) e t2 senza
% clausole rispondeva [t1,t2,t3] - t2 "riusciva" perche' a girare era t1.
%
% Quindi ogni caso qui sotto fa variare il goal ad ogni soluzione, e almeno uno
% dei goal deve fallire: se la metachiamata resta ferma sul primo, la lista
% delle soluzioni e' piu' lunga del dovuto, non piu' corta.

:- multifile(iso/3).

% mc_sel/1 elenca tre goal, ma mc_t2 non ha clausole
mc_sel(mc_t1).
mc_sel(mc_t2).
mc_sel(mc_t3).

mc_t1.
mc_t3.

% goal di nome e arita' diversi, per vedere se la seconda chiamata usa ancora
% il funtore della prima
mc_one(1).
mc_one(2).
mc_three(x).
mc_three(y).
mc_three(z).

mc_g(mc_one(_)).
mc_g(mc_three(_)).

% goal composti con argomenti gia' legati
mc_arg(mc_p(1)).
mc_arg(mc_p(2)).
mc_arg(mc_p(3)).

mc_p(1).
mc_p(3).

% --- una variabile come goal deve rileggersi ad ogni soluzione -------------
iso('7.8.3', (findall(G, (mc_sel(G), G), L), L == [mc_t1, mc_t3]),           success).
iso('7.8.3', (findall(G, (mc_sel(G), G, true), L), L == [mc_t1, mc_t3]),     success).
iso('7.8.3', (findall(G, (mc_sel(G), call(G)), L), L == [mc_t1, mc_t3]),     success).
iso('7.8.3', (findall(X, (member(X, [mc_t1, mc_t2, mc_t3]), X), L), L == [mc_t1, mc_t3]),  success).
iso('7.8.3', (findall(G, (mc_sel(G), G, G), L), L == [mc_t1, mc_t3]),        success).

% --- ... anche quando cambia il funtore, non solo gli argomenti ------------
iso('7.8.3', (findall(G, (mc_g(G), G), L), length(L, 5)),                    success).
iso('7.8.3', (findall(S, (mc_g(G), G, arg(1, G, S)), L), L == [1, 2, x, y, z]),  success).

% --- ... e quando il goal porta argomenti gia' legati ----------------------
iso('7.8.3', (findall(G, (mc_arg(G), G), L), L == [mc_p(1), mc_p(3)]),       success).

% --- le stesse dentro le costruzioni di controllo --------------------------
iso('7.8.3', (findall(G, (mc_sel(G), \+ \+ G), L), L == [mc_t1, mc_t3]),     success).
iso('7.8.3', (findall(G, (mc_sel(G), (G -> true ; fail)), L), L == [mc_t1, mc_t3]),  success).
iso('7.8.3', (findall(G, (mc_sel(G), catch(G, _, fail)), L), L == [mc_t1, mc_t3]),   success).
iso('7.8.3', (findall(G, (mc_sel(G), (G ; fail)), L), L == [mc_t1, mc_t3]),  success).
iso('7.8.3', (findall(G, (mc_sel(G), findall(_, G, [_])), L), L == [mc_t1, mc_t3]),  success).

% --- una variabile non legata come goal e' un errore di istanziazione ------
iso('7.8.3', (catch(call(_), error(E, _), true), E == instantiation_error),  success).
