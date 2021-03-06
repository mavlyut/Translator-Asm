package parsers;

import myBase.MyPair;

import java.util.ArrayList;
import java.util.Map;

import static parsers.ParserRVC.error;

public class ParserCommands {
    protected static Map<Integer, Integer> symTabNodesDict = null;
    protected static ArrayList<SymTabNode> symTabNodes = null;
    protected static int text_addr;

    private ParserCommands() {}

    protected static MyPair<String[], Boolean> parseCommand(int[] bytes, int left) {
        if (Integer.toBinaryString(bytes[left]).endsWith("11")) {
            return new MyPair<>(ParserCommands.parseRiscV(bytes, left), true);
        }
        return new MyPair<>(ParserCommands.parseRvc(bytes, left), false);
    }

    private static String[] parseRvc(int[] bytes, int left) {
        final String instruction = decToBin(bytes[left + 1]) + decToBin(bytes[left]);
        final String fun03 = instruction.substring(0, 3);
        final String fun49 = instruction.substring(4, 9);
        final String fun69 = instruction.substring(6, 9);
        final String fun314 = instruction.substring(3, 14);
        final String fun911 = instruction.substring(9, 11);
        final String fun914 = instruction.substring(9, 14);
        final String reg49 = reg(fun49);
        final String fun1114 = instruction.substring(11, 14);
        final String imm1 = "" + Integer.parseInt(Character.toString(instruction.charAt(3)), 2);
        final String imm2 = "" + Integer.parseInt(fun914, 2);
        return switch (Integer.parseInt(instruction.substring(14), 2)) {
            case 0 -> {
                if (instruction.startsWith("000")) {
                    yield new String[]{"c.addi4spn", reg(fun1114),
                            "sp", "" + Integer.parseInt(instruction.substring(3, 11), 2)};
                } else if (instruction.startsWith("100")) {
                    throw error(String.format("%s (it is reserved)", instruction));
                } else {
                    int imm11 = Integer.parseInt(instruction.substring(3, 6), 2);
                    int imm21 = Integer.parseInt(fun911, 2);
                    yield new String[]{
                            "c." + ParserRVC.quadrant0(fun03),
                            reg(fun69), "" + imm11,
                            reg(fun1114), "" + imm21
                    };
                }
            }
            case 1 -> {
                String rs1 = reg(fun69);
                String rs2 = reg(fun1114);
                int regB = Integer.parseInt(instruction.charAt(3) + fun911 + instruction.charAt(13)
                        + instruction.substring(4, 6) + instruction.substring(11, 13), 2);
                yield switch (Integer.parseInt(fun03, 2)) {
                    case 0 -> {
                        if (instruction.equals("0".repeat(15) + "1")) {
                            yield new String[]{"c.nop"};
                        } else {
                            yield new String[]{"c.addi", reg49, imm2};
                        }
                    }
                    case 1 -> new String[]{"c.jal", getLabel(Integer.parseInt(fun314, 2))};
                    case 2 -> new String[]{"c.li", reg49, imm2};
                    case 3 -> {
                        if (Integer.parseInt(fun49, 2) == 2) {
                            yield new String[]{"c.addi16sp", imm1, "sp", imm2};
                        } else {
                            yield new String[]{"c.lui", reg49, "" + (Integer.parseInt(imm1) + text_addr)};
                        }
                    }
                    case 4 -> switch (Integer.parseInt(instruction.substring(4, 6), 2)) {
                        case 0 -> new String[]{"c.srli", rs1, imm2};
                        case 1 -> new String[]{"c.srai", rs1, imm2};
                        case 2 -> new String[]{"c.andi", rs1, imm2};
                        case 3 -> new String[]{"c." + ParserRVC.quadrant1(instruction, imm1 + fun911), rs1, rs2};
                        default -> throw error(instruction);
                    };
                    case 5 -> new String[]{"c.j", getLabel(Integer.parseInt(
                            "" + instruction.charAt(3) + instruction.charAt(7) + instruction.substring(5, 7)
                                    + instruction.charAt(9) + instruction.charAt(8) + instruction.charAt(13)
                                    + instruction.charAt(4) + instruction.substring(10, 13), 2))};
                    case 6 -> new String[]{"c.beqz", rs1, getLabel(regB + 4)};
                    case 7 -> new String[]{"c.bnez", rs1, getLabel(regB + 4)};
                    default -> throw error(instruction);
                };
            }
            case 2 -> switch (Integer.parseInt(fun03, 2)) {
                case 0, 1, 2, 3 -> new String[]{String.format(
                        "c.%s %s, %s(sp)", ParserRVC.quadrant2(fun03), reg49, imm2
                )};
                case 4 -> {
                    if (instruction.charAt(3) == '0' && !isZeroes(fun49)) {
                        if (isZeroes(fun914)) {
                            yield new String[]{"c.jr", reg49};
                        } else {
                            yield new String[]{"c.mv", reg49, reg(fun914)};
                        }
                    } else if (instruction.charAt(3) == '1') {
                        if (isZeroes(fun49) && isZeroes(fun914)) {
                            yield new String[]{"c.ebreak"};
                        } else if (isZeroes(fun914)) {
                            yield new String[]{"c.jalr", reg49};
                        } else {
                            yield new String[]{"c.add", reg49, reg(fun914)};
                        }
                    } else {
                        throw error(instruction);
                    }
                }
                case 5, 6, 7 -> {
                    int imm = Integer.parseInt(instruction.substring(3, 9), 2);
                    yield new String[]{"c." + ParserRVC.quadrant2(fun03), reg(fun914), "" + imm};
                }
                default -> throw error(instruction);
            };
            default -> throw error(instruction);
        };
    }

    private static String[] parseRiscV(int[] bytes, int left) {
        StringBuilder sb = new StringBuilder()
                .append(decToBin(bytes[left + 3])).append(decToBin(bytes[left + 2]))
                .append(decToBin(bytes[left + 1])).append(decToBin(bytes[left]));
        String opcode = sb.substring(25, 32);
        String rd = sb.substring(20, 25);
        String func3 = sb.substring(17, 20);
        String rs1 = sb.substring(12, 17);
        String rs2 = sb.substring(7, 12);
        String func7 = sb.substring(0, 7);

        if (sb.toString().equals("00000000000000000000000001110011")) {
            return new String[] {"ecall"};
        } else if (sb.toString().equals("00000000000100000000000001110011")) {
            return new String[] {"ebreak"};
        }

        return switch (opcode) {
            case "0110011" -> new String[]{ParserRiscV.parseR(func7, func3), reg(rd), reg(rs1), reg(rs2)};
            case "1100011" -> {
                int imm_b = Integer.parseUnsignedInt(
                        (sb.charAt(0) + "").repeat(20) +
                                sb.charAt(24) + sb.substring(1, 7) + sb.substring(20, 24) + "0",2
                );
                yield new String[]{ParserRiscV.parseB(func3), reg(rs1), reg(rs2), getLabel(imm_b + 4)};
            }
            case "0100011" -> {
                int imm_s = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(20)
                        + sb.substring(0, 7) + sb.substring(20, 25), 2);
                yield new String[]{
                        String.format("%s %s, %s(%s)", ParserRiscV.parseS(func3), reg(rs2), "" + imm_s, reg(rs1))
                };
            }
            case "0110111", "0010111" -> {
                int imm_u = Integer.parseUnsignedInt(sb.substring(0, 20) + "0".repeat(12), 2);
                yield new String[]{ParserRiscV.parseU(opcode), reg(rd), imm_u + ""};
            }
            case "1110011" -> new String[]{ParserRiscV.parseICsr(func3), reg(rd), reg(sb.substring(0, 12)), reg(rs1)};
            case "0010011" -> {
                int imm_i = Integer.parseUnsignedInt(
                        Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 12), 2
                );
                yield new String[]{
                        ParserRiscV.parseISr(func3, func7), reg(rd), reg(rs1),
                        "" + (func3.equals("101") || func3.equals("001") ?
                                Integer.parseUnsignedInt(sb.substring(7, 12), 2) : imm_i)
                };
            }
            case "0000011" -> {
                int imm_i = Integer.parseUnsignedInt(
                        Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 12), 2
                );
                yield new String[]{
                        String.format("%s %s, %s(%s)", ParserRiscV.parseIL(func3), reg(rd), "" + imm_i, reg(rs1))
                };
            }
            case "1101111" -> {
                int imm_j = Integer.parseUnsignedInt(Character.toString(sb.charAt(0)).repeat(12)
                        + sb.substring(12, 20) + sb.charAt(11) + sb.substring(1, 11) + "0", 2);
                yield new String[]{"jal", reg(rd), getLabel(imm_j)};
            }
            case "1100111" -> {
                int imm_i = Integer.parseUnsignedInt(
                        Character.toString(sb.charAt(0)).repeat(20) + sb.substring(0, 12), 2
                );
                yield new String[]{"jalr", reg(rd), reg(rs1), "" + imm_i};
            }
            case "0001111" -> new String[]{"fence" + (func3.equals("001") ? ".i" : "")};
            default -> throw new ParserException("Risc-V", sb.toString());
        };
    }

    private static String reg(String a) {
        int reg = Integer.parseInt(a, 2);
        if (reg == 0) {
            return "zero";
        } else if (reg == 1) {
            return "ra";
        } else if (reg == 2) {
            return "sp";
        } else if (reg == 3) {
            return "gp";
        } else if (reg == 4) {
            return "tp";
        } else if (reg == 5) {
            return "t0";
        } else if (6 <= reg && reg <= 7) {
            String s = "t";
            s += (char) (reg - 5 + '0');
            return s;
        } else if (reg == 8) {
            return "s0";
        } else if (reg == 9) {
            return "s1";
        } else if (10 <= reg && reg <= 11) {
            String s = "a";
            s += (char) (reg - 10 + '0');
            return s;
        } else if (12 <= reg && reg <= 17) {
            String s = "a";
            s += (char) (reg - 10 + '0');
            return s;
        } else if (18 <= reg && reg <= 27) {
            String s = "s";
            s += (char) (reg - 16 + '0');
            return s;
        } else if (28 <= reg && reg <= 31) {
            String s = "t";
            s += (char) (reg - 25 + '0');
            return s;
        }
        return null;
    }

    protected static String decToBin(int b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(b % 2);
            b /= 2;
        }
        return sb.reverse().toString();
    }

    protected static boolean isZeroes(final String x) {
        return x.equals("0".repeat(x.length()));
    }

    private static String getLabel(int i) {
        final int addr_command = text_addr + i;
        if (symTabNodesDict.containsKey(addr_command)) {
            return symTabNodes.get(symTabNodesDict.get(addr_command)).getName();
        }
        symTabNodesDict.put(addr_command, symTabNodes.size());
        symTabNodes.add(new SymTabNode(String.format("LOC_%05x", addr_command)));
        return getLabel(i);
    }
}