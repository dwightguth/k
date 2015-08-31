open Prelude
open Constants
open Constants.K
open Def
let _ = let config = [Bottom] in 
  let out = open_out Sys.argv.[2] 
  and exit = open_out Sys.argv.[3] 
  and depth = int_of_string Sys.argv.[4] 
  and lexbuf = Lexing.from_channel (open_in Sys.argv.[1]) in 
  let res = try(run(Parser.top Lexer.token lexbuf) depth) with Stuck c' -> c' in
  output_string out (print_k res); output_string exit (get_exit_code res)
