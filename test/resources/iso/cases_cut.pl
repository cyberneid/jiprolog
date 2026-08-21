% ISO/IEC 13211-1 section 7.8.6 - il cut.
%
% Il cut e' la costruzione piu' facile da implementare quasi giusta: taglia i
% punti di scelta della clausola in cui appare e quelli dei goal alla sua
% sinistra nello stesso corpo, e non deve vedersi fuori da \+, ->, call/1 e
% findall. Ogni riga qui sotto e' una di quelle proprieta'.

:- multifile(iso/3).

cut_p(1).
cut_p(2).
cut_p(3).

% commette alla clausola e al primo p
cut_first(X) :- cut_p(X), !.

% il cut dopo il test: commette alla prima soluzione che passa il test
cut_gt(X) :- cut_p(X), X > 1, !.

% due clausole: il cut nella prima impedisce di provare la seconda
cut_two(a) :- !.
cut_two(b).

% il cut e' nel corpo della prima clausola ma dopo un fallimento
cut_late(X) :- cut_p(X), X > 5, !.
cut_late(none).

% cut dentro una disgiunzione
cut_disj(X) :- ( cut_p(X), ! ; X = other ).

% cut sotto \+
cut_naf :- \+ ( cut_p(_), !, fail ).

% cut nella condizione di un if-then-else: e' locale alla condizione
cut_cond(R) :- ( cut_p(_), ! -> R = then ; R = else ).

% cut dentro call/1: opaco verso l'esterno, ma dentro la call commette lo
% stesso - quindi una sola soluzione
cut_call(X) :- call((cut_p(X), !)).
cut_in_call_opaque(L) :- findall(X, (cut_p(X), call(!)), L).

% cut in un corpo con goal a destra
cut_then_more(X-Y) :- cut_p(X), !, cut_p(Y).

% --- commit alla clausola e ai goal a sinistra
iso('7.8.6', (findall(X, cut_first(X), L), L == [1]),                  success).
iso('7.8.6', (findall(X, cut_gt(X), L), L == [2]),                     success).
iso('7.8.6', (findall(X, cut_two(X), L), L == [a]),                    success).
iso('7.8.6', (cut_two(b)),                                             success).
iso('7.8.6', (findall(X, cut_late(X), L), L == [none]),                success).

% --- il cut non tocca i goal alla sua destra
iso('7.8.6', (findall(P, cut_then_more(P), L), L == [1-1, 1-2, 1-3]),  success).

% --- cut in disgiunzione: taglia l'intera clausola, quindi niente ramo destro
iso('7.8.6', (findall(X, cut_disj(X), L), L == [1]),                   success).

% --- cut al livello del goal
iso('7.8.6', (findall(X, (cut_p(X), !), L), L == [1]),                 success).
iso('7.8.6', (findall(X, (cut_p(X), !, fail), L), L == []),            success).
% il cut e' trasparente a ;/2: taglia l'alternativa, poi fail fallisce
iso('7.8.6', ((!, fail ; true)),                                       failure).
iso('7.8.6', (findall(X, ((X = 1 ; X = 2), !), L), L == [1]),          success).

% --- il cut e' locale a \+
iso('7.8.6', cut_naf,                                                  success).
iso('7.8.6', (findall(X, (cut_p(X), \+ (!, fail)), L), L == [1,2,3]),  success).

% --- il cut e' locale alla condizione di ->
iso('7.8.6', (cut_cond(R), R == then),                                 success).
iso('7.8.6', (findall(X, (cut_p(X), ( ! -> true ; true )), L), L == [1,2,3]),  success).

% --- il cut e' opaco a call/1
iso('7.8.7', (findall(X, cut_call(X), L), L == [1]),                    success).
iso('7.8.7', (cut_in_call_opaque(L), L == [1, 2, 3]),                  success).
iso('7.8.7', (findall(X, (cut_p(X), call((!, fail))), L), L == []),    success).
iso('7.8.7', ((call(!), fail ; true)),                                 success).

% --- il cut e' locale a findall
iso('8.10.1', (findall(X, (cut_p(X), !), L), L == [1]),                success).
iso('8.10.1', (findall(Y, (cut_p(Y), findall(X, (cut_p(X), !), _)), L), L == [1,2,3]),  success).

% --- if-then-else non lascia punti di scelta sulla condizione
iso('7.8.5', (findall(X, ( cut_p(X) -> true ; X = none ), L), L == [1]),  success).
iso('7.8.5', (findall(X, ( cut_p(X), X > 5 -> true ; X = none ), L), L == [none]),  success).

% --- il cut nel ramo "then" o "else" non e' locale: taglia la clausola
iso('7.8.5', (findall(X, ( cut_p(X), ( true -> ! ; true ) ), L), L == [1]),  success).

% --- soft cut: *-> non commette alla prima soluzione della condizione
iso('7.8.5', (findall(X, ( cut_p(X) *-> true ; X = none ), L), L == [1,2,3]),  success).
iso('7.8.5', (findall(X, ( cut_p(X), X > 5 *-> true ; X = none ), L), L == [none]),  success).

% --- il cut ripristina comunque i legami dei punti di scelta scartati
% stesso motivo: il ramo destro e' gia' stato tagliato quando si arriva a fail
iso('7.8.6', (( cut_p(X), !, fail ; true )),                           failure).
