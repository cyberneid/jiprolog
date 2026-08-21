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
