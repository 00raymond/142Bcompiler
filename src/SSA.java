import bb.BasicBlock;
import bb.ConstBasicBlock;
import graphvis.Graph;
import instruction.*;
import result.Result;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

public class SSA {

    private static int sp = 0;
    private static int bbp = 1;
    private static ArrayList<BasicBlock> basicBlocks = new ArrayList<>();

    public SSA() {
        ConstBasicBlock consts = new ConstBasicBlock(); // index 0 in basicBlocks array will always have consts
        basicBlocks.add(consts);

        BasicBlock bb = new BasicBlock();
        basicBlocks.add(bb);
    }

    public static void updatePhiInTargetBlock(String varName, int instructionNo, int targetBbp) {

        if (basicBlocks.get(targetBbp).getVarTable().containsKey(varName)) {
            // update the phi function with the new value.
            basicBlocks.get(targetBbp).changeInstructionBySymbol(varName, instructionNo);
            return;
        } else {
            // targetBbp must be set to the next while header block.
            int save = targetBbp;
            targetBbp = findNextWhileHeader(targetBbp - 1);
            if (targetBbp == -1) {
                targetBbp = findNextWhileHeader(save);
            }
            System.out.println(targetBbp);
            if (basicBlocks.get(targetBbp).getVarTable().containsKey(varName)) {
                // update the phi function with the new value.
                basicBlocks.get(targetBbp).changeInstructionBySymbol(varName, instructionNo);
                return;
            } else {
                // add it to the instructions of the targetBbp
                int find = findVariableSkipElseThenBody(varName);
                Instruction newPhiInstruct = new Instruction(sp, "phi", instructionNo, find);
                basicBlocks.get(targetBbp).addSymbol(varName, sp);
                basicBlocks.get(targetBbp).addInstruction(newPhiInstruct);
                sp++;

            }
        }
        System.out.println("could not find bbp with variable: " + varName);
    }

    public static void generatePhiFromLookAhead(String varName, int whileHeaderBbp) {
        // create phi function in the nearest up while loop header
        // for the variable varName.

        // if the variable has not been added to the while loop symbol table, add it.

        int prevInstructionNo = findVariable(varName, whileHeaderBbp - 1);
        // upon running the loop, we will find the true new value of the variable.
        Instruction phiInstruct = new Instruction(sp, "phi", -1, prevInstructionNo);
        basicBlocks.get(whileHeaderBbp).addSymbol(varName, sp);
        int newInstructionNo = sp;
        sp++;
        basicBlocks.get(whileHeaderBbp).addInstruction(phiInstruct);

        // if nestedLoop >= 2, that means we are in a nested while loop. we need to check the wrapping header(s) for the variable
        // and update the value of this phi function.

        if (Parser.getLoopDepth() > 1) {
            // we are in a nested while loop. need to check each wrapping header's symbol table for the variable and if its there
            // we need to update its phi function.

            int startingBbp = --whileHeaderBbp;
            System.out.println(startingBbp);
            for (int i = 0; i < Parser.getLoopDepth() - 1; i++) {

                while (!basicBlocks.get(startingBbp).isWhile()) {
                    startingBbp--;
                    System.out.println(startingBbp);
                }
                BasicBlock nextWhileHeader = basicBlocks.get(startingBbp);
                if (nextWhileHeader.getVarTable().containsKey(varName)) {
                    // update the phi function with the new value.
                    nextWhileHeader.changeInstructionBySymbol(varName, newInstructionNo);
                }

                startingBbp++;

            }

        }


    }

    public static void changeCurrBbWhile() {
        basicBlocks.get(bbp).setWhile(true);
    }

    public static void changeCurrBbWhileContinue() {
        basicBlocks.get(bbp).setWhileContinue(true);
    }

    public static void changeCurrBbWhileRel() {
        basicBlocks.get(bbp).setWhileRel(true);
    }

    public static int getBbp() {
        return bbp;
    }

    public static int findVariable(String symbol, int bbpStart) {
        // iterate backwards through basic blocks starting at bbpStart and ending at top of the basic blocks.
        // skip else, then, while header or while rel blocks.

        if (Parser.getLoopDepth() > 1 ) {
            for (int i = bbpStart; i > 0; i--) {
                BasicBlock basicBlock = basicBlocks.get(i);
                if (basicBlock.isElse() || basicBlock.isThen() || basicBlock.isWhileRel()) {
                    continue;
                }
                for (String sym : basicBlock.getVarTable().keySet()) {
                    if (sym.equals(symbol)) {
                        return basicBlock.getVarTable().get(sym);
                    }
                }
            }
            return -1;
        }

        for (int i = bbpStart; i > 0; i--) {
            BasicBlock basicBlock = basicBlocks.get(i);
            if (basicBlock.isElse() || basicBlock.isThen() || basicBlock.isWhile() || basicBlock.isWhileRel()) {
                continue;
            }
            for (String sym : basicBlock.getVarTable().keySet()) {
                if (sym.equals(symbol)) {
                    return basicBlock.getVarTable().get(sym);
                }
            }
        }
        return -1;
    }

    public static int addRead() {
        // need to add a read instruction
        ReadInstruction readInstruction = new ReadInstruction(sp);
        basicBlocks.get(bbp).addInstruction(readInstruction);
        int instructNo = sp;
        sp++;
        return instructNo;
    }

    public static void addWrite(Result src) {
        // src is the result of the expression. should be able to get the instructionSp from this src to reference in the write.
        WriteInstruction writeInstruction;
        if (src.getKind() == 1) {
            // its a variable
            writeInstruction = new WriteInstruction(sp, SSA.findVariableSkipElseThenBody(Tokenizer.Id2String(src.getValue())));
        } else {
            writeInstruction = new WriteInstruction(sp, src.getInstructionSp());
        }

        basicBlocks.get(bbp).addInstruction(writeInstruction);
        sp++;
    }


    public static int addCmp(Result x, Result y) {
        // need to add a compare instruction
        Instruction instruct = new Instruction(sp, "cmp", x.getInstructionSp(), y.getInstructionSp());
        basicBlocks.get(bbp).addInstruction(instruct);
        return sp++;
    }

    public static void addBra(int targetBbp) {
        // NO NEED FOR EMPTY INSTRUCTION! JUST POINT TO FIRST INSTRUCTION IN THE BASIC BLOCK.
        //adds branch instruction to basicblock at current pointer, also sources sp

        // find first instruction number in targetBbp
        int instructNo;

        // check if the first instruction in the targetBbp exists, if not, create an empty instruction
        if (basicBlocks.get(targetBbp).getInstructions().isEmpty()) {
            // create new emptyInstruction, add it to the bbp then point to it from the current bbp.
            EmptyInstruction emptyInstruction = new EmptyInstruction(sp);
            basicBlocks.get(targetBbp).addInstruction(emptyInstruction);
            instructNo = sp;
            sp++;


        } else {
            instructNo = basicBlocks.get(targetBbp).getInstructions().get(0).getInstructionNo();
        }


        BraInstruction braInstruction = new BraInstruction(sp, instructNo);
        basicBlocks.get(SSA.bbp).addInstruction(braInstruction);
        sp++;

    }

    public static void addBra() {
        //adds branch instruction to basicblock at current pointer, also sources sp
        BraInstruction braInstruction = new BraInstruction(sp, sp + 1);
        basicBlocks.get(bbp).addInstruction(braInstruction);
        sp++;
        bbp++;
        EmptyInstruction emptyInstruction = new EmptyInstruction(sp);

        if (basicBlocks.size() <= bbp) {
            BasicBlock bb = new BasicBlock();
            basicBlocks.add(bb);
        }

        basicBlocks.get(bbp).addInstruction(emptyInstruction);
        bbp--;
        sp++;

    }

    public static void addBranch(String br, int cmpInstructionNo) {
        // need to add a branch instruction
        Instruction instruct = new Instruction(sp, br, cmpInstructionNo, sp + 1);
        basicBlocks.get(bbp).addInstruction(instruct);
        sp++;
        // add target instruction(at sp + 1) in the next basic block
        bbp++;
        EmptyInstruction emptyInstruction = new EmptyInstruction(sp);
        
        if (basicBlocks.size() <= bbp) {
            BasicBlock bb = new BasicBlock();
            basicBlocks.add(bb);
        }

        basicBlocks.get(bbp).addInstruction(emptyInstruction);
        bbp--;
        sp++;
    }

    public static int addConst(int value) {
        int xInstructNo = findEqualConst(value);
        if (xInstructNo == -1) {
            xInstructNo = sp++;
            ConstInstruction constInstruct = new ConstInstruction(xInstructNo, value);
            ((ConstBasicBlock) basicBlocks.get(0)).addInstruction(constInstruct);
        }
        return xInstructNo;
    }

    public static int skipWhileBody(int startBbp) {
        int start = startBbp;
        if (SSA.basicBlocks.get(start).isWhileContinue()) {
            // do not use values from the while body.
            int tempBbp = bbp;
            while (!basicBlocks.get(tempBbp).isWhile()) {
                tempBbp--;
            }
            start = tempBbp;
        }
        return start;
    }

    public static int findVariableSkipElseThenBody(String symbol) {
        // iterate backwards through basic blocks starting at bbpStart and ending at top of the basic blocks.
        // skip else, then, while header or while rel blocks.

        int start = bbp;

        // check if its currently the end of a while block.
        if (basicBlocks.get(bbp).isWhile()) {
            start = skipWhileBody(bbp);
        }

        while (start > 0) {

            if (basicBlocks.get(start).isWhileContinue()) {
                System.out.println("currently at: " + start);
                Stack<BasicBlock> stack = new Stack<>();
                stack.push(basicBlocks.get(start));
                while (!stack.isEmpty()) {
                    System.out.println("currently at: " + start);

                    start = skipWhileBody(start);
                    start--;

                    if (basicBlocks.get(start).isWhileContinue()) {

                        stack.push(basicBlocks.get(start));
                    }
                    if (basicBlocks.get(start).isWhile()) {

                        stack.pop();
                    }

                }

                continue;
            }

            BasicBlock basicBlock = basicBlocks.get(start);
            if (basicBlock.isElse() || basicBlock.isThen()) {
                start--;
                continue;
            }
            for (String sym : basicBlock.getVarTable().keySet()) {
                if (sym.equals(symbol)) {
                    return basicBlock.getVarTable().get(sym);
                }
            }
            start--;

        }

        System.out.println("Warning: Variable " + symbol + " not initialized. Creating a constant instruction for 0.");
        return addConst(0);
    }

    public static int addInstruction(String op, Result x, Result y) {

        Instruction instruct = null;
        int ykind = y.getKind();
        int xkind = x.getKind();

        if (x.getInstructionSp() != -1 || y.getInstructionSp() != -1) {

            if (x.getInstructionSp() != -1 && y.getInstructionSp() != -1) {
                // they are both instructions
                instruct = new Instruction(sp, op, x.getInstructionSp(), y.getInstructionSp()); // ez
            } else if (x.getInstructionSp() == -1) {
                // y is the instruction and x is a constant/variable

                // check whether x is a constant or a variable
                if (x.getKind() == 0) {
                    // x is a constant
                    int instructNo = findEqualConst(x.getValue());
                    if (instructNo == -1) {
                        instructNo = sp;
                        ConstInstruction constInstruct = new ConstInstruction(instructNo, x.getValue());
                        ((ConstBasicBlock) basicBlocks.get(0)).addInstruction(constInstruct);
                        sp++;
                    }
                    instruct = new Instruction(sp, op, instructNo, y.getInstructionSp());
                } else {
                    // x is a variable
                    // extract x from symbol table
                    int tempBbp = bbp;
                    int xInstructNo = 0;
                    boolean found = false;
                    do {
                        BasicBlock tempBb = basicBlocks.get(tempBbp);
                        for (String symbol : tempBb.getVarTable().keySet()) {
                            if (symbol.equals(Tokenizer.Id2String(x.getValue()))) {
                                xInstructNo = tempBb.getVarTable().get(symbol);
                                found = true;
                            }
                        }
                        tempBbp--;
                    } while (tempBbp > 1);

                    int realPos = findVariableSkipElseThenBody(Tokenizer.Id2String(x.getValue()));

                    if (!found) {
                        System.out.println("Warning: Variable " + Tokenizer.Id2String(x.getValue()) + " not initialized. Creating a constant instruction for 0.");
                        xInstructNo = addConst(0);
                    }
                    instruct = new Instruction(sp, op, xInstructNo, y.getInstructionSp());
                }


            } else {
                // x is the instruction and y is a constant/variable

                // check whether y is a constant/variable
                if (y.getKind() == 0) {
                    // y is a constant
                    int instructNo = findEqualConst(y.getValue());
                    if (instructNo == -1) {
                        instructNo = sp;
                        ConstInstruction constInstruct = new ConstInstruction(instructNo, y.getValue());
                        ((ConstBasicBlock) basicBlocks.get(0)).addInstruction(constInstruct);
                        sp++;
                    }
                    instruct = new Instruction(sp, op, x.getInstructionSp(), instructNo);
                } else {
                    // y is a variable
                    // extract y from symbol table
                    int tempBbp = bbp;
                    int yInstructNo = -1;
                    boolean found = false;
                    do {
                        BasicBlock tempBb = basicBlocks.get(tempBbp);
                        for (String symbol : tempBb.getVarTable().keySet()) {
                            if (symbol.equals(Tokenizer.Id2String(y.getValue()))) {
                                yInstructNo = tempBb.getVarTable().get(symbol);
                                found = true;
                            }
                        }
                        tempBbp--;
                    } while (tempBbp > 1);
                    if (!found) {
                        System.out.println("Warning: Variable " + Tokenizer.Id2String(y.getValue()) + " not initialized. Creating a constant instruction for 0.");
                        yInstructNo = addConst(0);
                    }
                    instruct = new Instruction(sp, op, x.getInstructionSp(), yInstructNo);
                }


            }

            // add instruction to basic block
            // incremenet sp for next instruction

            int instructionSp = findInstruction(instruct);
            // returns sp so that can add a symbol to the symbol table in current bb
            if (instructionSp == -1) {
                basicBlocks.get(bbp).addInstruction(instruct);
                instructionSp = sp;
                sp++;
            }

            return instructionSp;
        }

        // check kind of x and y
        if (xkind == 0 || ykind == 0) {

            ConstBasicBlock consts = (ConstBasicBlock) basicBlocks.get(0);
            ArrayList<Integer> constsValues = consts.getConstValues();
            if (ykind == 0 && xkind == 0) {
                // both are constants, search ir for these consts if not found add them

                int xInstructNo = findEqualConst(x.getValue());
                if (xInstructNo == -1) {

                    xInstructNo = sp;
                    ConstInstruction constInstruct = new ConstInstruction(xInstructNo, x.getValue());
                    consts.addInstruction(constInstruct);

                    sp++;
                }

                int yInstructNo = findEqualConst(y.getValue());
                if (yInstructNo == -1) {
                    yInstructNo = sp;
                    ConstInstruction constInstruct = new ConstInstruction(yInstructNo, y.getValue());
                    consts.addInstruction(constInstruct);

                    sp++;
                }

                instruct = new Instruction(sp, op, xInstructNo, yInstructNo);

            } else if (ykind == 0) {
                // y is a constant, x is a var
                int instructNo = findEqualConst(y.getValue());

                if (instructNo == -1) {
                    instructNo = sp;
                    ConstInstruction constInstruct = new ConstInstruction(instructNo, y.getValue());
                    consts.addInstruction(constInstruct);

                    sp++;
                }
                // handle x var
                // extract x from symbol table

                int xPos = findVariableSkipElseThenBody(Tokenizer.Id2String(x.getValue()));

                if (xPos == -1) {
                    System.out.println("Warning: Variable " + Tokenizer.Id2String(x.getValue()) + " not initialized. Creating a constant instruction for 0.");
                    xPos = addConst(0);
                }
                instruct = new Instruction(sp, op, xPos, instructNo);

            } else {
                // x is a constant, y is a var
                int instructNo = findEqualConst(x.getValue());

                if (instructNo == -1) {
                    instructNo = sp;
                    ConstInstruction constInstruct = new ConstInstruction(instructNo, x.getValue());
                    consts.addInstruction(constInstruct);

                    sp++;
                }
                // handle y var
                // extract y from symbol table
                int yPos = findVariableSkipElseThenBody(Tokenizer.Id2String(y.getValue()));

                if (yPos == -1) {
                    System.out.println("Warning: Variable " + Tokenizer.Id2String(y.getValue()) + " not initialized. Creating a constant instruction for 0.");
                    yPos = addConst(0);
                }
                instruct = new Instruction(sp, op, yPos, instructNo);
            }

        }

        if (xkind == 1 && ykind == 1) {
            // both are vars
            // extract x and y from symbol table

            int xPos = findVariableSkipElseThenBody(Tokenizer.Id2String(x.getValue()));

            if (xPos == -1) {
                System.out.println("Warning: Variable " + Tokenizer.Id2String(x.getValue()) + " not initialized. Creating a constant instruction for 0.");
                xPos = addConst(0);
            }

            int yPos = findVariableSkipElseThenBody(Tokenizer.Id2String(y.getValue()));

            if (yPos == -1) {
                System.out.println("Warning: Variable " + Tokenizer.Id2String(y.getValue()) + " not initialized. Creating a constant instruction for 0.");
                yPos = addConst(0);
            }

            instruct = new Instruction(sp, op, xPos, yPos);

        }

        int instructionSp = findInstruction(instruct);
        // returns sp so that can add a symbol to the symbol table in current bb
        if (instructionSp == -1) {
            basicBlocks.get(bbp).addInstruction(instruct);
            instructionSp = sp;
            sp++;
        }

        return instructionSp;
    }

    public static int findInstruction(Instruction instruct) {
        for (BasicBlock bb : basicBlocks) {
            for (Instruction instruction : bb.getInstructions()) {
                if (instruction.equals(instruct)) {
                    return instruction.getInstructionNo();
                }
            }
        }
        return -1;
    }

    public static void addAssignmentToSymbolTable(String var, Result src) {
        // add var to symbol table at current bb pointer
        basicBlocks.get(bbp).addSymbol(var, src.getInstructionSp());
        basicBlocks.get(bbp).getVarTable().put(var, src.getInstructionSp());
    }

    public static void shiftBbpUp() {
        bbp++;
    }

    public static void shiftBbpDown() {
        bbp--;
    }

    public static void createBasicBlock() {
        BasicBlock bb = new BasicBlock();
        basicBlocks.add(bb);
        shiftBbpUp();
    }

    public static void changeCurrBbThen() {
        basicBlocks.get(bbp).setThen(true);
    }

    public static void changeCurrBbElse() {
        basicBlocks.get(bbp).setElse(true);
    }

    public static int findEqualConst(int value) {
        for (ConstInstruction constInstruction : ((ConstBasicBlock) basicBlocks.get(0)).getConstInstructions()) {
            if (constInstruction.getVal() == value) {
                return constInstruction.getInstructionNo();
            }
        }
        return -1;
    }

    public static int findNextWhileHeaderBbp(int bbpStart) {
        int start = bbpStart;
        while (start != 0) {
            if (basicBlocks.get(start).isWhile()) {
                return start;
            }
            start--;
        }
        return -1;
    }

    public static void printAll() {
//        for (int i = 0; i < basicBlocks.size(); i++) {
//            BasicBlock basicBlock = basicBlocks.get(i);
//
//            if (basicBlock.getClass() == ConstBasicBlock.class) {
//                System.out.println("Const Block:");
//                for (ConstInstruction constInstruction : ((ConstBasicBlock) basicBlock).getConstInstructions()) {
//                    System.out.println(constInstruction);
//                }
//
//            } else {
//
//                System.out.println("Basic Block " + i + ": ");
//                for (Instruction instruction : basicBlock.getInstructions()) {
//                    System.out.println(instruction);
//                }
//                System.out.println("Symbol Table " + i + ": ");
//                for (String symbol : basicBlock.getVarTable().keySet()) {
//                    System.out.println(symbol + " " + basicBlock.getVarTable().get(symbol));
//                }
//            }
//        }
//        System.out.println("bbp: " + bbp);

    }

    public static void generatePhi() {
        int firstBbp = bbp - 1;
        int secondBbp = bbp;
        int addPhiBbp = bbp + 1;

        System.out.println("reached this point");
        // set addphibbp to a join block
        if (basicBlocks.size() <= addPhiBbp) {
            BasicBlock bb = new BasicBlock();
            basicBlocks.add(bb);
        }
        basicBlocks.get(addPhiBbp).setJoin(true);
        System.out.println("reached this point asdf");

        BasicBlock firstBlock = basicBlocks.get(firstBbp);
        HashMap<String, Integer> firstSymTable = firstBlock.getVarTable();
        System.out.println("reached this point asdfasdf");

        BasicBlock secondBlock = basicBlocks.get(secondBbp);
        HashMap<String, Integer> secondSymTable = secondBlock.getVarTable();
        System.out.println("reached this point asdfasdfasdf");

        HashMap<String, Integer> tempMap = new HashMap<>();

        for (String symbol : firstSymTable.keySet()) {
            tempMap.put(symbol, firstSymTable.get(symbol));
        }
        System.out.println("reached this point asdf123124");

        for (String symbol : secondSymTable.keySet()) {
            if (tempMap.containsKey(symbol)) {
                //create a phi function instruction
                Instruction phiInstruct = new Instruction(sp, "phi", tempMap.get(symbol), secondSymTable.get(symbol));
                // add the phi function to the symbol table of the addPhiBbp, consisting of the instructions of the 2 keys
                basicBlocks.get(addPhiBbp).addInstruction(phiInstruct);
                // remove the symbol from the tempMap
                tempMap.remove(symbol);

                // add to symbol table the symbol and the instruction number of the phi function into the addPhiBbp symbol table
                basicBlocks.get(addPhiBbp).addSymbol(symbol, sp);

                sp++;
            } else {
                // add the symbol to the tempMap
                tempMap.put(symbol, secondSymTable.get(symbol));
            }
        }
        System.out.println("reached this point asdf1111111");

        System.out.println(tempMap);
        try {
            Iterator<String> iterator = tempMap.keySet().iterator();
            while (iterator.hasNext()) {
                String symbol = iterator.next();
                System.out.println("currently on" + symbol);
                boolean found = false;

                try {
                    for (int i = addPhiBbp - 2; i > 0; i--) {
                        BasicBlock tempBb = basicBlocks.get(i);
                        for (String sym : tempBb.getVarTable().keySet()) {
                            if (sym.equals(symbol)) {
                                System.out.println("here asf");
                                System.out.println(tempMap);

                                if (tempMap.get(symbol) == null) {
                                    System.out.println("its null bruh " + symbol + " " + tempBb.getVarTable().get(symbol));
                                    continue;
                                }
                                found = true;
                                Instruction phiInstruct = new Instruction(sp, "phi", tempMap.get(symbol), tempBb.getVarTable().get(symbol));
                                basicBlocks.get(addPhiBbp).addInstruction(phiInstruct);
                                iterator.remove(); // Remove the symbol from the tempMap

                                basicBlocks.get(addPhiBbp).addSymbol(symbol, sp);
                                sp++;
                                break;
                            }
                        }
                        if (found) break; // Exit the loop if the symbol was found and processed
                    }
                } catch (Exception e) {
                    System.out.println("Error: " + e);
                }

                System.out.println("reached this point asdf42444444444444444");

                if (!found) {
                    try {
                        Instruction phiInstruct = new Instruction(sp, "phi", tempMap.get(symbol), -1);
                        basicBlocks.get(addPhiBbp).addInstruction(phiInstruct);
                        iterator.remove(); // Remove the symbol from the tempMap

                        basicBlocks.get(addPhiBbp).addSymbol(symbol, sp);
                        sp++;
                    } catch (Exception e) {
                        System.out.println("Error hahaha: " + e);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error hehehe: " + e);
        }


        bbp++;
    }

    public static int findNextWhileHeader(int bbpStart) {
        int start = bbpStart;
        while (start != 0) {
            if (basicBlocks.get(start).isWhile()) {
                return start;
            }
            start--;
        }
        return -1;
    }

    public static void generateDotGraph() {
        // print each basic block number and if they are a then or else
//        for (int i = 0; i < basicBlocks.size(); i++) {
//            BasicBlock basicBlock = basicBlocks.get(i);
//            System.out.println("Basic Block " + i + " isThen: " + basicBlock.isThen() + " isElse: " + basicBlock.isElse() + " isJoin: " + basicBlock.isJoin() + " isWhile: " + basicBlock.isWhile() + " isWhileContinue: " + basicBlock.isWhileContinue() + " isWhileRel: " + basicBlock.isWhileRel());
//        }

        StringBuilder dotGraph = new StringBuilder();
        System.out.println("\n===============================> Dot Graph Code <=============================================\n");
        dotGraph.append("digraph G {\n");
        dotGraph.append("splines=line;\n");

        // generate nodes for each basic block
        // generate for const block
        dotGraph.append("bb0 [shape=record, label=\"BB0 | ");
        for (ConstInstruction constInstruction : ((ConstBasicBlock) basicBlocks.get(0)).getConstInstructions()) {
            dotGraph.append(constInstruction).append("\\l");
        }
        dotGraph.append("\"];\n");

        for (int i = 1; i < basicBlocks.size(); i++) {
            BasicBlock basicBlock = basicBlocks.get(i);
            if (basicBlock.getInstructions().isEmpty()) { continue; }
            dotGraph.append("bb").append(i).append(" [shape=record, label=\"BB").append(i).append(" | ");
            for (Instruction instruction : basicBlock.getInstructions()) {
                if (instruction.toString().contains("<empty>")) {
                    dotGraph.append(instruction.getInstructionNo() + ": &lt;empty&gt;\\l");
                } else {
                    dotGraph.append(instruction).append("\\l");
                }
            }
            dotGraph.append("\"];\n");
        }

        // iterate backwards through the basic blocks and add edges
        // if the basic block is a JOIN BLOCK => add edges from bb -1  to bb and bb -2 to bb.
        //create a stack of while continues. every time a while header is found, associate it with the top of the while continue stack, and then pop it.

        Stack<BasicBlock> whileContinueStack = new Stack<>();

        for (int i = basicBlocks.size() - 1; i >= 0; i--) {
            BasicBlock basicBlock = basicBlocks.get(i);
            if (basicBlock.getInstructions().isEmpty() && i == basicBlocks.size() - 1) { continue; }

            try {
                if (basicBlock.isWhile()) {
                    // top of the whilecontinue stack is the while continue block associated with this while header.
                    BasicBlock whileContinue = whileContinueStack.pop();
                    int whileContinueBbp = basicBlocks.indexOf(whileContinue);

                    // basicBlock + 1 should point to whileContinueBbp.
                    dotGraph.append("bb").append(i + 1).append(" -> bb").append(whileContinueBbp).append(" [label=\"follow\"];\n");

                    // if the stack is now empty, whileContinue doesnt need to point to anything. however, if it is not empty, it needs to point
                    // to the wrapping while header.
                    // the whilecontinue -1 also needs to point to the current while header.
                    dotGraph.append("bb").append(whileContinueBbp - 1).append(" -> bb").append(i).append(" [label=\"branch\"];\n");



                }
            } catch (Exception e) {
                System.out.println("Error hahah: " + e);
            }

            if (basicBlock.isWhileContinue()) {
                whileContinueStack.push(basicBlock);


//                if (basicBlocks.get(i-1).isWhileContinue()) {
//
//                    int nestedWhileHeader = findNextWhileHeader(i-1);
//                    int wrappingWhileHeader = findNextWhileHeader(nestedWhileHeader - 1);
//
//                    dotGraph.append("bb").append(nestedWhileHeader + 1).append(" -> bb").append(i-1).append(" [label=\"branch\"];\n");
//
//                    dotGraph.append("bb").append(wrappingWhileHeader + 1).append(" -> bb").append(i).append(" [label=\"fall-through\"];\n");
//                    dotGraph.append("bb").append(i-1).append(" -> bb").append(wrappingWhileHeader).append(" [label=\"branch\"];\n");
//
//                } else {
//
//                    int whileHeaderBbp = i;
//                    whileHeaderBbp = skipWhileBody(whileHeaderBbp);
//                    int whileHeaderRelBbp = whileHeaderBbp + 1;
//                    dotGraph.append("bb").append(whileHeaderBbp).append(" -> bb").append(whileHeaderRelBbp).append(";\n");
//                    dotGraph.append("bb").append(whileHeaderRelBbp).append(" -> bb").append(i).append(" [label=\"fall-through\"];\n");
//
//                    dotGraph.append("bb").append(whileHeaderRelBbp).append(" -> bb").append(whileHeaderRelBbp + 1).append(" [label=\"branch\"];\n");
//                    for (int j = whileHeaderRelBbp + 2; j < i; j++) {
//                        dotGraph.append("bb").append(j - 1).append(" -> bb").append(j).append(";\n");
//                    }
//                    dotGraph.append("bb").append(i - 1).append(" -> bb").append(whileHeaderBbp).append(" [label=\"branch\"];\n");
//                    i = whileHeaderBbp + 1;
//                }
            } else if (basicBlock.isJoin()) {
                dotGraph.append("bb").append(i - 1).append(" -> bb").append(i).append(" [label=\"branch\"];\n");
                dotGraph.append("bb").append(i - 2).append(" -> bb").append(i).append(" [label=\"fall-through\"];\n");

                if (basicBlocks.get(i-1).isElse()) {
                    // then i - 1 is else and i - 2 is then.
                    dotGraph.append("bb").append(i - 3).append(" -> bb").append(i - 1).append(" [label=\"fall-through\"];\n");
                    dotGraph.append("bb").append(i - 3).append(" -> bb").append(i - 2).append(" [label=\"branch\"];\n");
                } else {

                    dotGraph.append("bb").append(i - 2).append(" -> bb").append(i - 1).append(";\n");
                    dotGraph.append("bb").append(i - 2).append(" -> bb").append(i).append(";\n");
                }

                i -= 2;
            } else {
                if (i - 1 < 0) { break; }
                dotGraph.append("bb").append(i - 1).append(" -> bb").append(i).append(";\n");
            }
        }


        dotGraph.append("}\n");
        System.out.println(dotGraph);
        System.out.println("==================================> Code End <=============================================\n");
        Graph.createGraph(dotGraph.toString());
    }
}
