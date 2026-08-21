% ISO/IEC 13211-1 sections 8.6 (arithmetic evaluation), 8.7 (arithmetic
% comparison) and 9 (evaluable functors).

% --- 8.6.1 is/2
:- multifile(iso/3).

iso('8.6.1',  (X is 3, X == 3),                         success).
iso('8.6.1',  (X is 3 + 11, X == 14),                   success).
iso('8.6.1',  (X is 3 - 11, X == -8),                   success).
iso('8.6.1',  (X is 3 * 11, X == 33),                   success).
iso('8.6.1',  (X is -(-7), X == 7),                     success).
iso('8.6.1',  (X is 3.5, X == 3.5),                     success).
iso('8.6.1',  1 is 2,                                   failure).
iso('8.6.1',  _ is _,                                   error(instantiation_error)).
iso('8.6.1',  _ is foo,                                 error(type_error(evaluable, foo/0))).
iso('8.6.1',  _ is foo + 1,                             error(type_error(evaluable, foo/0))).
iso('8.6.1',  _ is "abc" + 1,                           error(_)).

% --- 8.7 arithmetic comparison
iso('8.7.1',  1 =:= 1,                                  success).
iso('8.7.1',  1 =:= 1.0,                                success).
iso('8.7.1',  1 =\= 2,                                  success).
iso('8.7.1',  1 < 2,                                    success).
iso('8.7.1',  2 < 1,                                    failure).
iso('8.7.1',  1 =< 1,                                   success).
iso('8.7.1',  2 > 1,                                    success).
iso('8.7.1',  1 >= 1,                                   success).
iso('8.7.1',  (1 + 1) =:= 2,                            success).
iso('8.7.1',  _ < 1,                                    error(instantiation_error)).

% --- 9.1 integer arithmetic
iso('9.1.3',  (X is 7 // 2, X == 3),                    success).
iso('9.1.3',  (X is -7 // 2, X == -3),                  success).
iso('9.1.3',  (X is 7 // -2, X == -3),                  success).
iso('9.1.3',  (X is 7 div 2, X == 3),                   success).
iso('9.1.3',  (X is -7 div 2, X == -4),                 success).
iso('9.1.3',  (X is 7 div -2, X == -4),                 success).
iso('9.1.3',  _ is 1 // 0,                              error(evaluation_error(zero_divisor))).
iso('9.1.3',  _ is 1 div 0,                             error(evaluation_error(zero_divisor))).
iso('9.1.3',  _ is 1 // 1.0,                            error(type_error(integer, 1.0))).
iso('9.1.3',  (X is 7 mod 2, X == 1),                   success).
iso('9.1.3',  (X is -7 mod 2, X == 1),                  success).
iso('9.1.3',  (X is 7 mod -2, X == -1),                 success).
iso('9.1.3',  (X is -7 mod -2, X == -1),                success).
iso('9.1.3',  _ is 1 mod 0,                             error(evaluation_error(zero_divisor))).
iso('9.1.3',  (X is 7 rem 2, X == 1),                   success).
iso('9.1.3',  (X is -7 rem 2, X == -1),                 success).
iso('9.1.3',  (X is 7 rem -2, X == 1),                  success).
iso('9.1.3',  _ is 1 rem 0,                             error(evaluation_error(zero_divisor))).

% --- 9.1 sign, abs, min, max
iso('9.1.3',  (X is abs(-3), X == 3),                   success).
iso('9.1.3',  (X is abs(3), X == 3),                    success).
iso('9.1.3',  (X is abs(-3.0), X == 3.0),               success).
iso('9.1.3',  (X is sign(-3), X == -1),                 success).
iso('9.1.3',  (X is sign(0), X == 0),                   success).
iso('9.1.3',  (X is sign(3), X == 1),                   success).
iso('9.1.3',  (X is min(1, 2), X == 1),                 success).
iso('9.1.3',  (X is max(1, 2), X == 2),                 success).

% --- 9.1 float conversion
iso('9.1.7',  (X is float(3), X == 3.0),                success).
iso('9.1.7',  (X is float(3.0), X == 3.0),              success).
iso('9.1.7',  (X is truncate(3.7), X == 3),             success).
iso('9.1.7',  (X is truncate(-3.7), X == -3),           success).
iso('9.1.7',  (X is round(3.7), X == 4),                success).
iso('9.1.7',  (X is ceiling(3.2), X == 4),              success).
iso('9.1.7',  (X is floor(3.7), X == 3),                success).
iso('9.1.7',  (X is floor(-3.2), X == -4),              success).
iso('9.1.7',  (X is float_integer_part(3.7), X == 3.0),     success).
iso('9.1.7',  (X is float_fractional_part(3.0), X == 0.0),  success).
iso('9.1.7',  _ is truncate(_),                         error(instantiation_error)).

% --- 9.3 other evaluable functors
iso('9.3.1',  (X is 2 ** 3, X == 8.0),                  success).
iso('9.3.1',  (X is 2.0 ** 3, X == 8.0),                success).
iso('9.3.10', (X is 2 ^ 3, X == 8),                     success).
iso('9.3.10', (X is 2.0 ^ 3, X == 8.0),                 success).
iso('9.3.2',  (X is sin(0.0), X == 0.0),                success).
iso('9.3.3',  (X is cos(0.0), X == 1.0),                success).
iso('9.3.6',  (X is exp(0.0), X == 1.0),                success).
iso('9.3.7',  _ is log(0),                              error(evaluation_error(undefined))).
iso('9.3.9',  (X is sqrt(4.0), X == 2.0),               success).

% --- 9.4 bitwise
iso('9.4.1',  (X is 10 >> 2, X == 2),                   success).
iso('9.4.2',  (X is 2 << 2, X == 8),                    success).
iso('9.4.2',  (X is 1 << 40, X == 1099511627776),       success).
iso('9.4.3',  (X is 10 /\ 12, X == 8),                  success).
iso('9.4.4',  (X is 10 \/ 12, X == 14),                 success).
iso('9.4.5',  (X is \ \ 10, X == 10),                   success).
iso('9.4.6',  (X is 10 xor 12, X == 6),                 success).
iso('9.4.1',  _ is 1.0 >> 2,                            error(type_error(integer, 1.0))).
