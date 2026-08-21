% ISO/IEC 13211-1 section 8.2 - unification, in depth.
%
% Il punto di questi casi non e' che 1 = 1: e' il percorso di *disfacimento*.
% Un'unificazione che lega qualche variabile e poi fallisce deve lasciare tutto
% slegato com'era. E' la parte che si rompe in silenzio, perche' il goal
% fallisce comunque - solo con dei legami di troppo rimasti in giro.

:- multifile(iso/3).

% --- legami che sopravvivono
iso('8.2.1', (X = f(Y), Y = a, X == f(a)),                        success).
iso('8.2.1', (X = Y, Y = Z, Z = a, X == a, Y == a),               success).
iso('8.2.1', (X = Y, X == Y, X \== a),                            success).
iso('8.2.1', (f(X, Y) = f(Y, a), X == a, Y == a),                 success).
iso('8.2.1', (f(X, X) = f(a, a), X == a),                         success).
iso('8.2.1', f(X, X) = f(a, b),                                   failure).
iso('8.2.1', (g(X, h(Y)) = g(h(Z), h(a)), X == h(Z), Y == a),     success).

% --- disfacimento: dopo il fallimento le variabili devono essere libere
iso('8.2.1', (\+ f(X, a) = f(1, b), var(X)),                      success).
iso('8.2.1', (\+ f(X, Y, a) = f(1, 2, b), var(X), var(Y)),        success).
iso('8.2.1', (\+ [X, Y, Z] = [1, 2], var(X), var(Y), var(Z)),     success).
iso('8.2.1', (\+ f(X, X) = f(a, b), var(X)),                      success).
iso('8.2.1', (\+ (X = Y, f(X, b) = f(a, c)), var(X), var(Y)),     success).
iso('8.2.1', (\+ g(h(X), b) = g(h(a), c), var(X)),                success).

% --- disfacimento attraverso un legame condiviso: X e Y sono la stessa cella
iso('8.2.1', (X = Y, \+ f(X, b) = f(a, c), var(X), var(Y), X == Y),  success).

% --- liste, anche parziali
iso('8.2.1', ([a, b|T] = [a, b, c], T == [c]),                    success).
iso('8.2.1', ([a|T] = [a, b, c], T == [b, c]),                    success).
iso('8.2.1', (L = [a|T], T = [b], L == [a, b]),                   success).
iso('8.2.1', [a, b] = [a, b, c],                                  failure).
iso('8.2.1', (\+ [a, b, c] = [a, b], true),                       success).

% --- termini profondi
iso('8.2.1', (f(g(h(i(X)))) = f(g(h(i(a)))), X == a),             success).
iso('8.2.1', f(g(h(i(a)))) = f(g(h(i(b)))),                       failure).
iso('8.2.1', (\+ f(g(h(X)), a) = f(g(h(1)), b), var(X)),          success).

% --- unificazione fra due variabili non legate, poi legate una volta sola
iso('8.2.1', (X = Y, X = a, Y == a),                              success).
iso('8.2.1', (X = Y, Y = a, X == a),                              success).

% --- occurs check
iso('8.2.4', unify_with_occurs_check(X, f(X)),                    failure).
iso('8.2.4', (unify_with_occurs_check(X, f(a)), X == f(a)),       success).
iso('8.2.4', (\+ unify_with_occurs_check(X, f(X)), var(X)),       success).

% --- copy_term non deve condividere variabili con l'originale
iso('8.5.4', (copy_term(f(X), C), C = f(a), var(X)),              success).
iso('8.5.4', (copy_term(f(X, X), f(A, B)), A == B),               success).
