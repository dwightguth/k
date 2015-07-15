open Gmp
open Constants
open Hashcons
module type S =
sig
  type m
  type s
  type t = kitem list hash_consed
 and kitem = KApply of klabel * t list
           | KToken of sort * string
           | InjectedKLabel of klabel
           | Map of sort * klabel * m
           | List of sort * klabel * t list
           | Set of sort * klabel * s
           | Int of Z.t
           | Float of FR.t * int * int
           | String of string
           | Bool of bool
           | Bottom
  val compare : t -> t -> int
 (* val compare_kitem : kitem -> kitem -> int*)
end 


module rec K : (S with type m = K.t Map.Make(K).t and type s = Set.Make(K).t)  = 
struct
  module KMap = Map.Make(K)
  module KSet = Set.Make(K)
  type m = K.t KMap.t
  and s = KSet.t
  and t = kitem list hash_consed
 and kitem = KApply of klabel * t list
           | KToken of sort * string
           | InjectedKLabel of klabel
           | Map of sort * klabel * m
           | List of sort * klabel * t list
           | Set of sort * klabel * s
           | Int of Z.t
           | Float of FR.t * int * int
           | String of string
           | Bool of bool
           | Bottom
    let compare c1 c2 = c2.tag - c1.tag
end

module KMap = Map.Make(K)
module KSet = Set.Make(K)

module KCompare : (HashedType with type t = K.kitem list) =
struct
  open K
  type t = kitem list
  let rec equal_klist c1 c2 = match (c1, c2) with
  | [], [] -> true
  | hd1 :: tl1, hd2 :: tl2 -> hd1.tag = hd2.tag && equal_klist tl1 tl2
  | _ -> false
  let equal_kitem c1 c2 = match (c1, c2) with
  | KApply (kl1, k1), KApply (kl2, k2) -> kl1 = kl2 && equal_klist k1 k2
  | KToken (s1, st1), KToken (s2, st2) -> s1 = s2 && st1 = st2
  | InjectedKLabel kl1, InjectedKLabel kl2 -> kl1 = kl2
  | Map (_, l1, m1), Map (_, l2, m2) -> l1 = l2 && (KMap.compare compare m1 m2) = 0
  | List (_, lbl1, l1), List (_, lbl2, l2) -> lbl1 = lbl2 && equal_klist l1 l2
  | Set (_, l1, s1), Set (_, l2, s2) -> l1 = l2 && (KSet.compare s1 s2) = 0
  | Int i1, Int i2 -> Z.equal i1 i2
  | Float (f1,e1,p1), Float (f2,e2,p2) -> e1 = e2 && p1 = p2 && (FR.compare f1 f2) = 0
  | String s1, String s2 -> s1 = s2
  | Bool b1, Bool b2 -> b1 = b2
  | Bottom, Bottom -> true
  | _ -> false
  let rec equal c1 c2 = match (c1, c2) with
  | [], [] -> true
  | hd1 :: tl1, hd2 :: tl2 -> equal_kitem hd1 hd2 && equal tl1 tl2
  | _ -> false
  let rec hash_klist c = match c with
  | [] -> 1
  | hd :: tl -> (hash_klist tl) * 31 + hd.hkey
  let hash_kitem c = match c with
  | KApply (kl, k) -> (Hashtbl.hash kl) * 37 + (hash_klist k)
  | KToken (s, st) -> (Hashtbl.hash s) * 41 + (Hashtbl.hash st)
  | InjectedKLabel kl -> Hashtbl.hash kl
  | Map (_, l, m) -> (Hashtbl.hash l) * 43 + (KMap.fold (fun k v sum -> sum + (k.hkey lxor v.hkey)) m 0)
  | List (_, lbl, l) -> (Hashtbl.hash l) * 47 + (hash_klist l)
  | Set (_, l, s) -> (Hashtbl.hash l) * 53 + (KSet.fold (fun k sum -> sum + k.hkey)) s 0
  | Int i -> Hashtbl.hash i
  | Float (f,e,p) -> ((Hashtbl.hash e) * 59 + Hashtbl.hash p) * 59 + Hashtbl.hash f
  | String s -> Hashtbl.hash s
  | Bool b -> Hashtbl.hash b
  | Bottom -> 2
  let rec hash c = match c with
  | [] -> 1
  | hd :: tl -> (hash tl) * 31 + (hash_kitem hd)
end

module KHashCons = Hashcons.Make(KCompare)

let global_hash = KHashCons.create 128

let h = KHashCons.hashcons global_hash

open K
type k = K.t

let (@!) : k -> k -> k = fun a b -> h (a.node @ b.node)
let (@!!) : kitem -> k -> k = fun a b -> h (a :: b.node)

let compare_kitem (c1: kitem) (c2: kitem) = compare (h [c1]) (h [c2])
exception Stuck of k
exception Not_implemented
module GuardElt = struct
  type t = Guard of int
  let compare c1 c2 = match c1 with Guard(i1) -> match c2 with Guard(i2) -> i2 - i1
end
module Guard = Set.Make(GuardElt)
let freshCounter : Z.t ref = ref Z.zero
let isTrue(c: k) : bool = match c.node with
| ([(Bool true)]) -> true
| _ -> false
let rec list_range (c: k list * int * int) : k list = match c with
| (_, 0, 0) -> []
| (head :: tail, 0, len) -> head :: list_range(tail, 0, len - 1)
| (_ :: tail, n, len) -> list_range(tail, n - 1, len)
| ([], _, _) -> raise(Failure "list_range")
let float_to_string (f: FR.t) : string = if FR.is_nan f then "NaN" else if FR.is_inf f then if FR.sgn f > 0 then "Infinity" else "-Infinity" else FR.to_string f
let k_of_list lbl l = match l with 
  [] -> KApply((unit_for lbl),[])
| hd :: tl -> List.fold_left (fun list el -> KApply(lbl, (h [list]) :: (h [KApply((el_for lbl),[el])] :: []))) (KApply((el_for lbl),[hd])) tl
let k_of_set lbl s = if (KSet.cardinal s) = 0 then KApply((unit_for lbl),[]) else 
  let hd = KSet.choose s in KSet.fold (fun el set -> KApply(lbl, (h [set]) :: (h [KApply((el_for lbl),[el])] :: []))) (KSet.remove hd s) (KApply((el_for lbl),[hd]))
let k_of_map lbl m = if (KMap.cardinal m) = 0 then KApply((unit_for lbl),[]) else 
  let (k,v) = KMap.choose m in KMap.fold (fun k v map -> KApply(lbl, (h [map]) :: (h [KApply((el_for lbl),[k;v])] :: []))) (KMap.remove k m) (KApply((el_for lbl),[k;v]))
let rec print_klist(c: k list) : string = match c with
| [] -> ".KList"
| e::[] -> print_k(e.node)
| e1::e2::l -> print_k(e1.node) ^ ", " ^ print_klist(e2::l)
and print_k(c: kitem list) : string = match c with
| [] -> ".K"
| e::[] -> print_kitem(e)
| e1::e2::l -> print_kitem(e1) ^ " ~> " ^ print_k(e2::l)
and print_kitem(c: kitem) : string = match c with
| KApply(klabel, klist) -> print_klabel(klabel) ^ "(" ^ print_klist(klist) ^ ")"
| KToken(sort, s) -> "#token(\"" ^ (String.escaped s) ^ "\", \"" ^ print_sort(sort) ^ "\")"
| InjectedKLabel(klabel) -> "#klabel(" ^ print_klabel(klabel) ^ ")"
| Bool(b) -> print_kitem(KToken(Constants.boolSort, string_of_bool(b)))
| String(s) -> print_kitem(KToken(Constants.stringSort, "\"" ^ (String.escaped s) ^ "\""))
| Int(i) -> print_kitem(KToken(Constants.intSort, Z.to_string(i)))
| Float(f,_,_) -> print_kitem(KToken(Constants.floatSort, float_to_string(f)))
| Bottom -> "`#Bottom`(.KList)"
| List(_,lbl,l) -> print_kitem(k_of_list lbl l)
| Set(_,lbl,s) -> print_kitem(k_of_set lbl s)
| Map(_,lbl,m) -> print_kitem(k_of_map lbl m)
module Subst = Map.Make(String)
let print_subst (out: out_channel) (c: k Subst.t) : unit = 
  output_string out "1\n"; Subst.iter (fun v k -> output_string out (v ^ "\n" ^ (print_k k.node) ^ "\n")) c
let emin (exp: int) (prec: int) : int = (- (1 lsl (exp - 1))) + 4 - prec
let emax (exp: int) : int = 1 lsl (exp - 1)
let round_to_range (c: kitem) : kitem = match c with 
    Float(f,e,p) -> let (cr, t) = (FR.check_range p GMP_RNDN (emin e p) (emax e) f) in Float((FR.subnormalize cr t GMP_RNDN),e,p)
  | _ -> raise (Invalid_argument "round_to_range")
let curr_fd : Z.t ref = ref (Z.of_int 3)
let file_descriptors = let m = Hashtbl.create 5 in Hashtbl.add m (Z.from_int 0) Unix.stdin; Hashtbl.add m (Z.from_int 1) Unix.stdout; Hashtbl.add m (Z.from_int 2) Unix.stderr; m
let default_file_perm = let v = Unix.umask 0 in let _ = Unix.umask v in (lnot v) land 0o777
let convert_open_flags (s: string) : Unix.open_flag list = 
  match s with 
      "r" -> [Unix.O_RDONLY] 
    | "w" -> [Unix.O_WRONLY] 
    | "rw" -> [Unix.O_RDWR]
    | _ -> raise (Invalid_argument "convert_open_flags")

let bottom = h [Bottom]
let dotk = h []
let true_k = h [Bool true]
let false_k = h [Bool false]

module MAP =
struct

  let hook_element c lbl sort config ff = match c with 
      k1 :: k2 :: [] -> h [Map (sort,(collection_for lbl),(KMap.singleton k1 k2))]
    | _ -> raise Not_implemented
  let hook_unit c lbl sort config ff = match c with 
      [] -> h [Map (sort,(collection_for lbl),KMap.empty)]
    | _ -> raise Not_implemented
  let hook_concat c lbl sort config ff = match c with 
      ({node=[Map (s,l1,k1)]}) :: ({node=[Map (_,l2,k2)]}) :: [] 
        when (l1 = l2) -> h [Map (s,l1,(KMap.merge (fun k a b -> match a, b with 
                          None, None -> None 
                        | None, Some v 
                        | Some v, None -> Some v 
                        | Some v1, Some v2 when v1 = v2 -> Some v1) k1 k2))]
    | _ -> raise Not_implemented
  let hook_lookup c lbl sort config ff = match c with 
      {node= [Map (_,_,k1)]} :: k2 :: [] -> (try KMap.find k2 k1 with Not_found -> bottom)
    | _ -> raise Not_implemented
  let hook_update c lbl sort config ff = match c with 
      {node=[Map (s,l,k1)]} :: k :: v :: [] -> h [Map (s,l,(KMap.add k v k1))]
    | _ -> raise Not_implemented
  let hook_remove c lbl sort config ff = match c with 
      {node=[Map (s,l,k1)]} :: k2 :: [] -> h [Map (s,l,(KMap.remove k2 k1))]
    | _ -> raise Not_implemented
  let hook_difference c lbl sort config ff = match c with
      {node=[Map (s,l1,k1)]} :: {node=[Map (_,l2,k2)]} :: []
        when (l1 = l2) -> h [Map (s,l1,(KMap.filter (fun k v -> try (compare (KMap.find k k2) v) <> 0 with Not_found -> true) k1))]
    | _ -> raise Not_implemented
  let hook_keys c lbl sort config ff = match c with 
      {node=[Map (_,_,k1)]} :: [] -> h [Set (Constants.setSort, Constants.setConcatLabel,(KMap.fold (fun k v -> KSet.add k) k1 KSet.empty))]
    | _ -> raise Not_implemented
  let hook_values c lbl sort config ff = match c with 
      {node=[Map (_,_,k1)]} :: [] -> h [List (Constants.listSort, Constants.listConcatLabel,(match List.split (KMap.bindings k1) with (_,vs) -> vs))]
    | _ -> raise Not_implemented
  let hook_choice c lbl sort config ff = match c with 
      {node=[Map (_,_,k1)]} :: [] -> (match KMap.choose k1 with (k, _) -> k)
    | _ -> raise Not_implemented
  let hook_size c lbl sort config ff = match c with 
      {node=[Map (_,_,m)]} :: [] -> h [Int (Z.of_int (KMap.cardinal m))]
    | _ -> raise Not_implemented
  let hook_inclusion c lbl sort config ff = match c with
      {node=[Map (s,l1,k1)]} :: {node=[Map (_,l2,k2)]} :: [] 
        when (l1 = l2) -> h [Bool (KMap.for_all (fun k v -> try (compare (KMap.find k k2) v) = 0 with Not_found -> false) k1)]
    | _ -> raise Not_implemented
  let hook_updateAll c lbl sort config ff = match c with 
      ({node=[Map (s,l1,k1)]}) :: ({node=[Map (_,l2,k2)]}) :: [] 
        when (l1 = l2) -> h [Map (s,l1,(KMap.merge (fun k a b -> match a, b with 
                          None, None -> None 
                        | None, Some v 
                        | Some v, None 
                        | Some _, Some v -> Some v) k1 k2))]
    | _ -> raise Not_implemented
  let hook_removeAll c lbl sort config ff = match c with
      {node=[Map (s,l,k1)]} :: {node=[Set (_,_,k2)]} :: [] -> h [Map (s,l,KMap.filter (fun k v -> not (KSet.mem k k2)) k1)]
    | _ -> raise Not_implemented
end

module SET =
struct
  let hook_in c lbl sort config ff = match c with
      k1 :: {node=[Set (_,_,k2)]} :: [] -> h [Bool (KSet.mem k1 k2)]
    | _ -> raise Not_implemented
  let hook_unit c lbl sort config ff = match c with
      [] -> h [Set (sort,(collection_for lbl),KSet.empty)]
    | _ -> raise Not_implemented
  let hook_element c lbl sort config ff = match c with
      k :: [] -> h [Set (sort,(collection_for lbl),(KSet.singleton k))]
    | _ -> raise Not_implemented
  let hook_concat c lbl sort config ff = match c with
      {node=[Set (sort,l1,s1)]} :: {node=[Set (_,l2,s2)]} :: [] when (l1 = l2) -> h [Set (sort,l1,(KSet.union s1 s2))]
    | _ -> raise Not_implemented
  let hook_difference c lbl sort config ff = match c with
      {node=[Set (s,l1,k1)]} :: {node=[Set (_,l2,k2)]} :: [] when (l1 = l2) -> h [Set (s,l1,(KSet.diff k1 k2))]
    | _ -> raise Not_implemented
  let hook_inclusion c lbl sort config ff = match c with
      {node=[Set (s,l1,k1)]} :: {node=[Set (_,l2,k2)]} :: [] when (l1 = l2) -> h [Bool (KSet.subset k1 k2)]
    | _ -> raise Not_implemented
  let hook_intersection c lbl sort config ff = match c with
      {node=[Set (s,l1,k1)]} :: {node=[Set (_,l2,k2)]} :: [] when (l1 = l2) -> h [Set (s,l1,(KSet.inter k1 k2))]
    | _ -> raise Not_implemented
  let hook_choice c lbl sort config ff = match c with
      {node=[Set (_,_,k1)]} :: [] -> KSet.choose k1
    | _ -> raise Not_implemented
  let hook_size c lbl sort config ff = match c with
      {node=[Set (_,_,s)]} :: [] -> h [Int (Z.of_int (KSet.cardinal s))]
    | _ -> raise Not_implemented
end

module LIST =
struct
  let hook_unit c lbl sort config ff = match c with
      [] -> h [List (sort,(collection_for lbl),[])]
    | _ -> raise Not_implemented
  let hook_element c lbl sort config ff = match c with
      k :: [] -> h [List (sort,(collection_for lbl),[k])]
    | _ -> raise Not_implemented
  let hook_concat c lbl sort config ff = match c with
      {node=[List (s,lbl1,l1)]} :: {node=[List (_,lbl2,l2)]} :: [] when (lbl1 = lbl2) -> h [List (s,lbl1,(l1 @ l2))]
    | _ -> raise Not_implemented
  let hook_in c lbl sort config ff = match c with
      k1 :: {node=[List (_,_,k2)]} :: [] -> h [Bool (List.mem k1 k2)]
    | _ -> raise Not_implemented
  let hook_get c lbl sort config ff = match c with
      {node=[List (_,_,l1)]} :: {node=[Int i]} :: [] -> 
        let i = Z.to_int i in (try if i >= 0 then List.nth l1 i else List.nth l1 ((List.length l1) + i) 
                               with Failure "nth" -> bottom | Invalid_argument "List.nth" -> bottom)
    | _ -> raise Not_implemented
  let hook_range c lbl sort config ff = match c with
      {node=[List (s,lbl,l1)]} :: {node=[Int i1]} :: {node=[Int i2]} :: [] -> 
        (try h [List (s,lbl,(list_range (l1, (Z.to_int i1), (List.length(l1) - (Z.to_int i2) - (Z.to_int i1)))))] 
         with Failure "list_range" -> bottom)
    | _ -> raise Not_implemented
  let hook_size c lbl sort config ff = match c with
      {node=[List (_,_,l)]} :: [] -> h [Int (Z.of_int (List.length l))]
    | _ -> raise Not_implemented
end

module KREFLECTION = 
struct
  let hook_sort c lbl sort config ff = match c with
      
      {node=[KToken (sort, s)]} :: [] -> h [String (print_sort(sort))]
    | {node=[Int _]} :: [] -> h [String "Int"]
    | {node=[String _]} :: [] -> h [String "String"]
    | {node=[Bool _]} :: [] -> h [String "Bool"]
    | {node=[Map (s,_,_)]} :: [] -> h [String (print_sort s)]
    | {node=[List (s,_,_)]} :: [] -> h [String (print_sort s)]
    | {node=[Set (s,_,_)]} :: [] -> h [String (print_sort s)]
    | _ -> raise Not_implemented
  let hook_getKLabel c lbl sort config ff = match c with
      {node=[KApply (lbl, _)]} :: [] -> h [InjectedKLabel lbl] | _ -> bottom
    | _ -> raise Not_implemented
  let hook_configuration c lbl sort config ff = match c with
      [] -> config
    | _ -> raise Not_implemented
  let hook_fresh c lbl sort config ff = match c with
      {node=[String sort]} :: [] -> let res = ff sort config !freshCounter in freshCounter := Z.add !freshCounter Z.one; res
    | _ -> raise Not_implemented
end

module KEQUAL =
struct
  let hook_eq c lbl sort config ff = match c with
      k1 :: k2 :: [] -> h [Bool ((compare k1 k2) = 0)]
    | _ -> raise Not_implemented
  let hook_ne c lbl sort config ff = match c with
      k1 :: k2 :: [] -> h [Bool ((compare k1 k2) <> 0)]
    | _ -> raise Not_implemented
  let hook_ite c lbl sort config ff = match c with
      {node=[Bool t]} :: k1 :: k2 :: [] -> if t then k1 else k2
    | _ -> raise Not_implemented
end

module IO =
struct
  let hook_close c lbl sort config ff = match c with
      {node=[Int i]} :: [] -> Unix.close (Hashtbl.find file_descriptors i); dotk
    | _ -> raise Not_implemented
  let hook_getc c lbl sort config ff = match c with
      {node=[Int i]} :: [] -> let b = Bytes.create 1 in let _ = Unix.read (Hashtbl.find file_descriptors i) b 0 1 in h [Int (Z.from_int (Char.code (Bytes.get b 0)))]
    | _ -> raise Not_implemented
  let hook_open c lbl sort config ff = match c with
      {node=[String path]} :: {node=[String flags]} :: [] -> 
        let fd = Unix.openfile path (convert_open_flags flags) default_file_perm in
          let fd_int = !curr_fd in Hashtbl.add file_descriptors fd_int fd; curr_fd := (Z.add fd_int Z.one); h [Int fd_int]
    | _ -> raise Not_implemented
  let hook_putc c lbl sort config ff = match c with
      {node=[Int fd]} :: {node=[Int c]} :: [] -> let _ = Unix.write (Hashtbl.find file_descriptors fd) (Bytes.make 1 (Char.chr (Z.to_int c))) 0 1 in dotk
    | _ -> raise Not_implemented
  let hook_read c lbl sort config ff = match c with
      {node=[Int fd]} :: {node=[Int len]} :: [] -> let l = (Z.to_int len) in 
        let b = Bytes.create l in let _ = Unix.read (Hashtbl.find file_descriptors fd) b 0 l in h [String (Bytes.to_string b)]
    | _ -> raise Not_implemented
  let hook_seek c lbl sort config ff = match c with
      {node=[Int fd]} :: {node=[Int off]} :: [] -> let o = (Z.to_int off) in let _ = Unix.lseek (Hashtbl.find file_descriptors fd) o Unix.SEEK_SET in dotk
    | _ -> raise Not_implemented
  let hook_tell c lbl sort config ff = match c with
      {node=[Int fd]} :: [] -> h [Int (Z.of_int (Unix.lseek (Hashtbl.find file_descriptors fd) 0 Unix.SEEK_CUR))]
    | _ -> raise Not_implemented
  let hook_write c lbl sort config ff = match c with
      {node=[Int fd]} :: {node=[String s]} :: [] -> let b = Bytes.of_string s in let _ = Unix.write (Hashtbl.find file_descriptors fd) b 0 (Bytes.length b) in dotk
    | _ -> raise Not_implemented

  let hook_stat c lbl sort config ff = raise Not_implemented
  let hook_lstat c lbl sort config ff = raise Not_implemented
  let hook_opendir c lbl sort config ff = raise Not_implemented
  let hook_parse c lbl sort config ff = raise Not_implemented
  let hook_parseInModule c lbl sort config ff = raise Not_implemented
  let hook_system c lbl sort config ff = raise Not_implemented
end

module BOOL = 
struct
  let hook_and c lbl sort config ff = match c with
      {node=[Bool b1]} :: {node=[Bool b2]} :: [] -> h [Bool (b1 && b2)]
    | _ -> raise Not_implemented
  let hook_andThen = hook_and
  let hook_or c lbl sort config ff = match c with
      {node=[Bool b1]} :: {node=[Bool b2]} :: [] -> h [Bool (b1 || b2)]
    | _ -> raise Not_implemented
  let hook_orElse = hook_or
  let hook_not c lbl sort config ff = match c with
      {node=[Bool b1]} :: [] -> h [Bool (not b1)]
    | _ -> raise Not_implemented
  let hook_implies c lbl sort config ff = match c with
      {node=[Bool b1]} :: {node=[Bool b2]} :: [] -> h [Bool ((not b1) || b2)]
    | _ -> raise Not_implemented
  let hook_ne c lbl sort config ff = match c with
      {node=[Bool b1]} :: {node=[Bool b2]} :: [] -> h [Bool (b1 <> b2)]
    | _ -> raise Not_implemented
  let hook_eq c lbl sort config ff = match c with
      {node=[Bool b1]} :: {node=[Bool b2]} :: [] -> h [Bool (b1 = b2)]
    | _ -> raise Not_implemented
  let hook_xor = hook_ne
end

module STRING =
struct
  let hook_concat c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [String (s1 ^ s2)]
    | _ -> raise Not_implemented
  let hook_lt c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [Bool ((String.compare s1 s2) < 0)]
    | _ -> raise Not_implemented
  let hook_le c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [Bool ((String.compare s1 s2) <= 0)]
    | _ -> raise Not_implemented
  let hook_gt c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [Bool ((String.compare s1 s2) > 0)]
    | _ -> raise Not_implemented
  let hook_ge c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [Bool ((String.compare s1 s2) >= 0)]
    | _ -> raise Not_implemented
  let hook_eq c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [Bool (s1 = s2)]
    | _ -> raise Not_implemented
  let hook_ne c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: [] -> h [Bool (s1 <> s2)]
    | _ -> raise Not_implemented
  let hook_chr c lbl sort config ff = match c with
      {node=[Int i]} :: [] -> h [String (String.make 1 (Char.chr (Z.to_int i)))]
    | _ -> raise Not_implemented
  let hook_find c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: {node=[Int i]} :: [] -> 
        (try h [Int (Z.of_int (Str.search_forward (Str.regexp_string s2) s1 (Z.to_int i)))] 
        with Not_found -> h [Int (Z.of_int (-1))])
    | _ -> raise Not_implemented
  let hook_rfind c lbl sort config ff = match c with
      {node=[String s1]} :: {node=[String s2]} :: {node=[Int i]} :: [] -> 
        (try h [Int (Z.of_int (Str.search_backward (Str.regexp_string s2) s1 (Z.to_int i)))] 
        with Not_found -> h [Int (Z.of_int (-1))])
    | _ -> raise Not_implemented
  let hook_length c lbl sort config ff = match c with
      {node=[String s]} :: [] -> h [Int (Z.of_int (String.length s))]
    | _ -> raise Not_implemented
  let hook_substr c lbl sort config ff = match c with
      {node=[String s]} :: {node=[Int i1]} :: {node=[Int i2]} :: [] -> h [String (String.sub s (Z.to_int i1) (Z.to_int (Z.sub i2 i1)))]
    | _ -> raise Not_implemented
  let hook_ord c lbl sort config ff = match c with
      {node=[String s]} :: [] -> h [Int (Z.of_int (Char.code (String.get s 0)))]
    | _ -> raise Not_implemented
  let hook_int2string c lbl sort config ff = match c with
      {node=[Int i]} :: [] -> h [String (Z.to_string i)]
    | _ -> raise Not_implemented
  let hook_string2int c lbl sort config ff = match c with
      {node=[String s]} :: [] -> h [Int (Z.from_string s)]
    | _ -> raise Not_implemented
  let hook_string2base c lbl sort config ff = match c with
      {node=[String s]} :: {node=[Int i]} :: [] -> h [Int (Z.from_string_base (Z.to_int i) s)]
    | _ -> raise Not_implemented
  let hook_base2string c lbl sort config ff = match c with
      {node=[Int i]} :: {node=[Int base]} :: [] -> h [String (Z.to_string_base (Z.to_int base) i)]
    | _ -> raise Not_implemented
  let hook_floatFormat c lbl sort config ff = raise Not_implemented
  let hook_float2string c lbl sort config ff = raise Not_implemented
  let hook_string2float c lbl sort config ff = raise Not_implemented
  let hook_replace c lbl sort config ff = raise Not_implemented
  let hook_replaceAll c lbl sort config ff = raise Not_implemented
  let hook_replaceFirst c lbl sort config ff = raise Not_implemented
  let hook_countAllOccurrences c lbl sort config ff = raise Not_implemented
  let hook_category c lbl sort config ff = raise Not_implemented
  let hook_directionality c lbl sort config ff = raise Not_implemented
  let hook_findChar c lbl sort config ff = raise Not_implemented
  let hook_rfindChar c lbl sort config ff = raise Not_implemented
end

module INT =
struct
  let hook_tmod c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.tdiv_r a b)]
    | _ -> raise Not_implemented
  let hook_add c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.add a b)]
    | _ -> raise Not_implemented
  let hook_le c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Bool ((Z.compare a b) <= 0)]
    | _ -> raise Not_implemented
  let hook_eq c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Bool ((Z.compare a b) = 0)]
    | _ -> raise Not_implemented
  let hook_ne c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Bool ((Z.compare a b) <> 0)]
    | _ -> raise Not_implemented
  let hook_and c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.band a b)]
    | _ -> raise Not_implemented
  let hook_mul c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.mul a b)]
    | _ -> raise Not_implemented
  let hook_sub c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.sub a b)]
    | _ -> raise Not_implemented
  let hook_tdiv c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.tdiv_q a b)]
    | _ -> raise Not_implemented
  let hook_shl c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.mul_2exp a (Z.to_int b))]
    | _ -> raise Not_implemented
  let hook_lt c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Bool ((Z.compare a b) < 0)]
    | _ -> raise Not_implemented
  let hook_ge c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Bool ((Z.compare a b) >= 0)]
    | _ -> raise Not_implemented
  let hook_shr c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.fdiv_q_2exp a (Z.to_int b))]
    | _ -> raise Not_implemented
  let hook_gt c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Bool ((Z.compare a b) > 0)]
    | _ -> raise Not_implemented
  let hook_pow c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.pow_ui a (Z.to_int b))]
    | _ -> raise Not_implemented
  let hook_xor c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.bxor a b)]
    | _ -> raise Not_implemented
  let hook_or c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.bior a b)]
    | _ -> raise Not_implemented
  let hook_not c lbl sort config ff = match c with
      {node=[Int a]} :: [] -> h [Int (Z.bcom a)]
    | _ -> raise Not_implemented
  let hook_abs c lbl sort config ff = match c with
      {node=[Int a]} :: [] -> h [Int (Z.abs a)]
    | _ -> raise Not_implemented
  let hook_max c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.max a b)]
    | _ -> raise Not_implemented
  let hook_min c lbl sort config ff = match c with
      {node=[Int a]} :: {node=[Int b]} :: [] -> h [Int (Z.min a b)]
    | _ -> raise Not_implemented
  let hook_ediv c lbl sort config ff = raise Not_implemented
  let hook_emod c lbl sort config ff = raise Not_implemented
end

module FLOAT =
struct
  let hook_isNaN c lbl sort config ff = match c with
      {node=[Float (f,_,_)]} :: [] -> h [Bool (FR.is_nan f)]
    | _ -> raise Not_implemented
  let hook_maxValue c lbl sort config ff = match c with
      {node=[Int prec]} :: {node=[Int exp]} :: [] -> let e = Z.to_int exp and p = Z.to_int prec in
        h [round_to_range(Float ((FR.nexttoward (emin e p) (emax e) (FR.from_string_prec_base p GMP_RNDN 10 "inf") FR.zero),e,p))]
    | _ -> raise Not_implemented
  let hook_minValue c lbl sort config ff = match c with
      {node=[Int prec]} :: {node=[Int exp]} :: [] -> let e = Z.to_int exp and p = Z.to_int prec in
        h [round_to_range(Float ((FR.nexttoward (emin e p) (emax e) FR.zero (FR.from_string_prec_base p GMP_RNDN 10 "inf")),e,p))]
    | _ -> raise Not_implemented
  let hook_round c lbl sort config ff = match c with
      {node=[Float (f,_,_)]} :: {node=[Int prec]} :: {node=[Int exp]} :: [] ->
        h [round_to_range (Float (f,(Z.to_int exp),(Z.to_int prec)))]
    | _ -> raise Not_implemented
  let hook_abs c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.abs_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_ceil c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.rint_prec p GMP_RNDU f),e,p))]
    | _ -> raise Not_implemented
  let hook_floor c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.rint_prec p GMP_RNDD f),e,p))]
    | _ -> raise Not_implemented
  let hook_acos c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.acos_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_asin c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.asin_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_atan c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.atan_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_cos c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.cos_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_sin c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.sin_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_tan c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.tan_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_exp c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.exp_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_log c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.log_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_neg c lbl sort config ff = match c with
      {node=[Float (f,e,p)]} :: [] -> h [round_to_range(Float ((FR.neg_prec p GMP_RNDN f),e,p))]
    | _ -> raise Not_implemented
  let hook_add c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] 
        when e1 = e2 && p1 = p2 -> h [round_to_range(Float ((FR.add_prec p1 GMP_RNDN f1 f2),e1,p1))]
    | _ -> raise Not_implemented
  let hook_sub c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] 
        when e1 = e2 && p1 = p2 -> h [round_to_range(Float ((FR.sub_prec p1 GMP_RNDN f1 f2),e1,p1))]
    | _ -> raise Not_implemented
  let hook_mul c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] 
        when e1 = e2 && p1 = p2 -> h [round_to_range(Float ((FR.mul_prec p1 GMP_RNDN f1 f2),e1,p1))]
    | _ -> raise Not_implemented
  let hook_div c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] 
        when e1 = e2 && p1 = p2 -> h [round_to_range(Float ((FR.div_prec p1 GMP_RNDN f1 f2),e1,p1))]
    | _ -> raise Not_implemented
  let hook_pow c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: []
        when e1 = e2 && p1 = p2 -> h [round_to_range(Float ((FR.pow_prec p1 GMP_RNDN f1 f2),e1,p1))]
    | _ -> raise Not_implemented
  let hook_eq c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] -> h [Bool (FR.equal f1 f2)]
    | _ -> raise Not_implemented
  let hook_lt c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] -> h [Bool (FR.less f1 f2)]
    | _ -> raise Not_implemented
  let hook_le c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] -> h [Bool (FR.lessequal f1 f2)]
    | _ -> raise Not_implemented
  let hook_gt c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] -> h [Bool (FR.greater f1 f2)]
    | _ -> raise Not_implemented
  let hook_ge c lbl sort config ff = match c with
      {node=[Float (f1,e1,p1)]} :: {node=[Float (f2,e2,p2)]} :: [] -> h [Bool (FR.greaterequal f1 f2)]
    | _ -> raise Not_implemented
  let hook_precision c lbl sort config ff = match c with
      {node=[Float (_,_,p)]} :: [] -> h [Int (Z.from_int p)]
    | _ -> raise Not_implemented
  let hook_exponentBits c lbl sort config ff = match c with
      {node=[Float (_,e,_)]} :: [] -> h [Int (Z.from_int e)]
    | _ -> raise Not_implemented
  let hook_float2int c lbl sort config ff = match c with
      {node=[Float (f,_,_)]} :: [] -> h [Int (FR.to_z_f f)]
    | _ -> raise Not_implemented
  let hook_int2float c lbl sort config ff = match c with
      {node=[Int i]} :: {node=[Int prec]} :: {node=[Int exp]} :: [] -> 
        h [round_to_range(Float ((FR.from_z_prec (Z.to_int prec) GMP_RNDN i),(Z.to_int exp),(Z.to_int prec)))]
    | _ -> raise Not_implemented
  let hook_min c lbl sort config ff = raise Not_implemented
  let hook_max c lbl sort config ff = raise Not_implemented
  let hook_rem c lbl sort config ff = raise Not_implemented
  let hook_root c lbl sort config ff = raise Not_implemented
  let hook_sign c lbl sort config ff = raise Not_implemented
  let hook_significand c lbl sort config ff = raise Not_implemented
  let hook_atan2 c lbl sort config ff = raise Not_implemented
  let hook_exponent c lbl sort config ff = raise Not_implemented
end
