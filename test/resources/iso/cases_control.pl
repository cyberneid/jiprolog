% ISO/IEC 13211-1 section 7.8 - control constructs, and 8.15 - negation.

:- multifile(iso/3).

iso('7.8.1',  true,                                     success).
iso('7.8.2',  fail,                                     failure).
iso('7.8.3',  (true, true),                             success).
iso('7.8.3',  (true, fail),                             failure).
iso('7.8.3',  (fail, true),                             failure).
iso('7.8.4',  (true ; fail),                            success).
iso('7.8.4',  (fail ; true),                            success).
iso('7.8.4',  (fail ; fail),                            failure).
iso('7.8.4',  ((X = 1 ; X = 2), X == 2),                success).
iso('7.8.5',  (true -> true),                           success).
iso('7.8.5',  (fail -> true),                           failure).
iso('7.8.5',  ((true -> Y = a ; Y = b), Y == a),        success).
iso('7.8.5',  ((fail -> Z = a ; Z = b), Z == b),        success).
iso('7.8.6',  (! ; true),                               success).
iso('7.8.7',  call(true),                               success).
iso('7.8.7',  call(fail),                               failure).
iso('7.8.7',  call((true, true)),                       success).
iso('7.8.7',  call(_),                                  error(instantiation_error)).
iso('7.8.7',  call(1),                                  error(type_error(callable, 1))).
iso('7.8.7',  call((true, 1)),                          error(type_error(callable, _))).
iso('7.8.8',  catch(true, _, fail),                     success).
iso('7.8.9',  catch(throw(ball), B, B == ball),         success).
% throw/1 with a non-error ball: the catcher b does not unify with a,
% so the ball propagates unchanged
iso('7.8.9',  catch(throw(a), b, true),                 ball(a)).
iso('7.8.9',  throw(_),                                 error(instantiation_error)).
iso('7.8.9',  catch(catch(throw(x), y, true), x, true), success).

iso('8.15.1', \+ fail,                                  success).
iso('8.15.1', \+ true,                                  failure).
iso('8.15.1', \+ _,                                     error(instantiation_error)).
iso('8.15.1', \+ 3,                                     error(type_error(callable, 3))).

% cut is opaque to call/1 and transparent in a clause body
iso('7.8.6',  (call((!, fail)) ; true),                 success).
