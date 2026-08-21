% ISO/IEC 13211-1 sezione 8.2.2 - unify_with_occurs_check/2, e cosa fa questa
% implementazione con i termini ciclici.
%
% =/2 non fa l'occurs check (8.2.1.3: il risultato su X = f(X) e' lasciato
% indefinito), quindi X = f(X) qui costruisce davvero un termine ciclico.
% I casi sotto fissano le due cose che *sono* definite: che
% unify_with_occurs_check/2 rifiuta, e che le operazioni che guardano il
% termine a profondita' limitata (functor/3, arg/3, ==/2 su se stesso)
% terminano invece di girare a vuoto. Quello che NON e' coperto qui, perche'
% non termina, e' l'unificazione di due termini ciclici distinti: vedi
% CODE_REVIEW.md sezione 16.

:- multifile(iso/3).

% --- unify_with_occurs_check/2 rifiuta il ciclo ---------------------------
iso('8.2.2', unify_with_occurs_check(_X, f(_X)),                      failure).
iso('8.2.2', (unify_with_occurs_check(X, f(X))),                      failure).
iso('8.2.2', (unify_with_occurs_check(X, [X])),                       failure).
iso('8.2.2', (unify_with_occurs_check(X, [a,b|X])),                   failure).
iso('8.2.2', (unify_with_occurs_check(f(A, B), f(g(B), A))),          failure).
iso('8.2.2', (unify_with_occurs_check(X, f(a, g(b, X)))),             failure).

% --- e accetta tutto il resto, come =/2 ------------------------------------
iso('8.2.2', (unify_with_occurs_check(X, f(a)), X == f(a)),           success).
iso('8.2.2', (unify_with_occurs_check(f(a, B), f(A, b)), A == a, B == b),  success).
iso('8.2.2', (unify_with_occurs_check(X, Y), X == Y),                 success).
iso('8.2.2', (unify_with_occurs_check([1,2,3], [1,2,3])),             success).
iso('8.2.2', unify_with_occurs_check(a, b),                           failure).
iso('8.2.2', (unify_with_occurs_check(f(X), f(g(Y))), X == g(Y)),     success).
iso('8.2.2', (unify_with_occurs_check(X, f(Y)), unify_with_occurs_check(Y, a), X == f(a)),  success).

% --- =/2 invece lega, e il termine risultante e' davvero ciclico -----------
iso('8.2.1', (X = f(X), nonvar(X)),                                   success).
iso('8.2.1', (X = f(X), arg(1, X, A), A == X),                        success).
iso('8.2.1', (X = f(X), functor(X, N, Ar), N == f, Ar == 1),          success).
iso('8.2.1', (X = f(X), X = f(_)),                                    success).

% --- occurs check e backtracking: un fallimento non lascia legami ----------
iso('8.2.2', (\+ unify_with_occurs_check(X, f(X)), var(X)),           success).
iso('8.2.2', (\+ unify_with_occurs_check(f(a, X), f(b, f(X))), var(X)),  success).
