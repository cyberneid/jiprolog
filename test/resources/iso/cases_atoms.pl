% ISO/IEC 13211-1 section 8.16 - atomic term processing.

:- multifile(iso/3).

iso('8.16.1', (atom_length(abc, N), N == 3),            success).
iso('8.16.1', (atom_length('', N), N == 0),             success).
iso('8.16.1', (atom_length('enchanted evening', N), N == 17),  success).
iso('8.16.1', atom_length(abc, 4),                      failure).
iso('8.16.1', atom_length(_, _),                        error(instantiation_error)).
iso('8.16.1', atom_length(1.23, _),                     error(type_error(atom, 1.23))).
iso('8.16.1', atom_length(atom, a),                     error(type_error(integer, a))).
iso('8.16.1', atom_length(atom, -1),                    error(domain_error(not_less_than_zero, -1))).

iso('8.16.2', (atom_concat(hello, world, A), A == helloworld),  success).
iso('8.16.2', (atom_concat(T, world, helloworld), T == hello),  success).
iso('8.16.2', (findall(A-B, atom_concat(A, B, abc), L), length(L, 4)),  success).
iso('8.16.2', atom_concat(_, _, _),                     error(instantiation_error)).
iso('8.16.2', atom_concat(a, _, _),                     error(instantiation_error)).

iso('8.16.3', (sub_atom(abracadabra, 0, 5, _, S), S == abrac),  success).
iso('8.16.3', (sub_atom(abracadabra, _, 5, 0, S), S == dabra),  success).
iso('8.16.3', (sub_atom(abracadabra, 3, 2, _, S), S == ac),     success).
iso('8.16.3', (findall(S, sub_atom(abc, _, 1, _, S), L), L == [a, b, c]),  success).
iso('8.16.3', sub_atom(_, _, _, _, _),                  error(instantiation_error)).
iso('8.16.3', sub_atom(1, _, _, _, _),                  error(type_error(atom, 1))).

iso('8.16.4', (atom_chars('', L), L == []),             success).
iso('8.16.4', (atom_chars([], L), L == ['[', ']']),     success).
iso('8.16.4', (atom_chars(abc, L), L == [a, b, c]),     success).
iso('8.16.4', (atom_chars(A, [a, b, c]), A == abc),     success).
iso('8.16.4', atom_chars(_, _),                         error(instantiation_error)).
iso('8.16.4', atom_chars(_, [a, _]),                    error(instantiation_error)).

iso('8.16.5', (atom_codes(abc, L), L == [0'a, 0'b, 0'c]),  success).
iso('8.16.5', (atom_codes(A, [0'a, 0'b]), A == ab),        success).
iso('8.16.5', atom_codes(_, _),                            error(instantiation_error)).

iso('8.16.6', (char_code(a, C), C == 0'a),              success).
iso('8.16.6', (char_code(C, 0'a), C == a),              success).
iso('8.16.6', char_code(_, _),                          error(instantiation_error)).

iso('8.16.7', (number_chars(33, L), L == ['3', '3']),   success).
iso('8.16.7', (number_chars(N, ['3', '3']), N == 33),   success).
iso('8.16.7', (number_chars(N, ['-', '2']), N == -2),   success).
iso('8.16.7', number_chars(_, _),                       error(instantiation_error)).

iso('8.16.8', (number_codes(33, L), L == [0'3, 0'3]),   success).
iso('8.16.8', (number_codes(N, [0'3, 0'3]), N == 33),   success).
iso('8.16.8', number_codes(_, _),                       error(instantiation_error)).

% --- number_chars/2 e number_codes/2 col numero gia' legato -----------------
% ISO 8.16.4.1: quando la lista c'e', si analizza e il numero si unifica col
% risultato. Andava nell'altra direzione - rendeva il numero nel suo testo
% canonico e confrontava - quindi lo stesso numero riusciva o falliva secondo
% come lo si era scritto. Ogni caso qui sotto e' una scrittura non canonica.
iso('8.16.7', number_chars(3.3, ['3', '.', '3']),                        success).
iso('8.16.7', number_chars(3.3, ['3', '.', '3', 'E', '+', '0']),         success).
iso('8.16.7', number_chars(4.2, ['4', '2', '.', '0', 'e', '-', '1']),    success).
iso('8.16.7', number_chars(33, [' ', '3', '3']),                         success).
iso('8.16.7', number_chars(15, ['0', 'x', 'f']),                         success).
iso('8.16.7', number_chars(65, ['0', '''', 'A']),                        success).
iso('8.16.7', number_chars(-25, ['-', '2', '5']),                        success).
iso('8.16.8', number_codes(33.0, [0'3, 0'., 0'3, 0'E, 0'+, 0'0, 0'1]),   success).
iso('8.16.8', number_codes(15, [0'0, 0'x, 0'f]),                         success).

% --- e la stessa lista letta all'indietro da' lo stesso numero -------------
iso('8.16.7', (number_chars(N, ['3', '.', '3', 'E', '+', '0']), N == 3.3),  success).
iso('8.16.7', (number_chars(N, ['0', 'x', 'f']), N == 15),                  success).
iso('8.16.7', (number_chars(N, ['0', 'o', '1', '7']), N == 15),             success).
iso('8.16.7', (number_chars(N, ['0', 'b', '1', '0', '1']), N == 5),         success).
iso('8.16.7', (number_chars(N, ['\n', ' ', '3']), N == 3),                  success).

% --- cio' che non e' un token numerico ISO e' un errore di sintassi --------
% Il ramo decimale usava Double.parseDouble, che accetta la sintassi dei
% letterali Java: 3d valeva 3, 3.3f valeva 3.3, Infinity valeva 2147483647 e
% NaN valeva 0.
iso('6.4.4', number_chars(_, ['3', 'd']),                                error(syntax_error(_))).
iso('6.4.4', number_chars(_, ['3', '.', '3', 'f']),                      error(syntax_error(_))).
iso('6.4.4', number_chars(_, ['I', 'n', 'f', 'i', 'n', 'i', 't', 'y']),  error(syntax_error(_))).
iso('6.4.4', number_chars(_, ['N', 'a', 'N']),                           error(syntax_error(_))).
% un float vuole cifre da entrambi i lati del punto
iso('6.4.4', number_chars(_, ['.', '3']),                                error(syntax_error(_))).
iso('6.4.4', number_chars(_, ['3', '.']),                                error(syntax_error(_))).
% layout in coda
iso('8.16.7', number_chars(_, ['3', ' ']),                               error(syntax_error(_))).
iso('6.4.4', number_chars(_, ['0', 'x', 'g']),                           error(syntax_error(_))).
iso('6.4.4', number_chars(_, ['0', 'o', '9']),                           error(syntax_error(_))).

% --- generazione: gli interi grandi non vengono troncati a 32 bit ----------
iso('8.16.7', (number_chars(3000000000, L), L == ['3','0','0','0','0','0','0','0','0','0']),  success).
