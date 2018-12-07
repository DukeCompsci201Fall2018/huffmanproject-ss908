import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out){
		int[] counts=readForCounts(in);//makes an arraylist of frequencies of each letter where the bit code for the
		//letter is the index and the value in the array is the frequency
		HuffNode root=makeTreeFromCounts(counts);//makes a tree using the greedy algorithm such that more frequently used
		//characters use less bits
		String[] codings= makeCodingsFromTree(root);//makes an array where each position is the bit for the letter and the
		//the value at that position is the the binary coding to navigate the tree
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);//writes the header so that the tree can be built
		in.reset();
		writeCompressedBits(codings,in,out);//goes through each of the array of pos is 8 bit chunk and the thing at that pos
		//is its binary coding and uses that to
		out.close();
	}
	private int[] readForCounts(BitInputStream in){
		int[] ans=new int[ALPH_SIZE + 1];
		int x=in.readBits(BITS_PER_WORD);
		while(x !=-1){
			ans[x]+=1;
			x=in.readBits(BITS_PER_WORD);
		}
		ans[PSEUDO_EOF]=1;
		return ans;
	}
	private HuffNode makeTreeFromCounts(int[] counts){
		PriorityQueue<HuffNode> pq=new PriorityQueue<>();
		if(myDebugLevel>=DEBUG_HIGH){
			System.out.printf("pq created with %d nodes \n",pq.size());
		}
		for(int i=0;i<counts.length;i++){
			if(counts[i]>0){
				pq.add(new HuffNode(i,counts[i],null,null));
			}
		}
		while(pq.size()>1){
			HuffNode left=pq.remove();
			HuffNode right=pq.remove();
			HuffNode t=new HuffNode(0,left.myWeight+right.myWeight,left,right);
			pq.add(t);
		}
		HuffNode root= pq.remove();
		return root;
	}
	private String[] makeCodingsFromTree(HuffNode root){
		String[] encodings=new String[ALPH_SIZE+1];
		help(encodings, root, "");
		return encodings;
	}
	private void help(String[] ans, HuffNode root, String path){
		if(root.myLeft==null&&root.myRight==null){
			ans[root.myValue]=path;
			if(myDebugLevel>=DEBUG_HIGH){
				System.out.printf("encoding for %d is %s \n",root.myValue,path);
			}
			return;
		}
		help(ans,root.myLeft,path+"0");
		help(ans,root.myRight,path+"1");
	}
	private void writeHeader(HuffNode node,BitOutputStream out){
		if(node.myLeft==null&&node.myRight==null){
			out.writeBits(1,1);
			out.writeBits(BITS_PER_WORD+1,node.myValue);
			return;
		}
		out.writeBits(1,0);
		writeHeader(node.myLeft,out);
		writeHeader(node.myRight,out);
	}
	private void writeCompressedBits(String[] encoding, BitInputStream in,BitOutputStream out){
		int x=in.readBits(BITS_PER_WORD+1);
		while(x !=PSEUDO_EOF){
			out.writeBits(encoding[x].length(),Integer.parseInt(encoding[x],2));
			in.readBits(BITS_PER_WORD+1);
		}
		out.writeBits(encoding[x].length(),Integer.parseInt(encoding[x],2));
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		int bits=in.readBits(BITS_PER_INT);
		if(bits!=HUFF_TREE) {
			throw new HuffException("illegal header starts with" + bits);
		}
		if(bits==-1){
			throw new HuffException("illegal header start with" + bits);
		}
		HuffNode root=readTreeHeader(in);//reads the tree header to build the tree for letters
		readCompressedBits(root,in,out);//read the bits after the header and translating it using the tree
		out.close();
	}
	private HuffNode readTreeHeader(BitInputStream in){
		int bit=in.readBits(1);
		if(bit==-1){throw new HuffException("-1 bit bit");}
		if(bit==0){
			return new HuffNode(0,0,readTreeHeader(in),readTreeHeader(in));
		}
		else{
			int value= in.readBits(BITS_PER_WORD+1);
			return new HuffNode(value,0,null,null);
		}
	}
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out){
		HuffNode current=root;
		while(true){
			int bit=in.readBits(1);
			if(bit==-1){
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			else{
				if(bit==0){
					current=current.myLeft;
				}
				else{
					current=current.myRight;
				}
				if(current.myLeft==null&&current.myRight==null){
					if(current.myValue==PSEUDO_EOF){
						break;
					}
					else{
						out.writeBits(BITS_PER_WORD,current.myValue);
						current=root;
					}
				}
			}
		}
	}
}