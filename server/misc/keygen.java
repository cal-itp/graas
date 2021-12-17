public class keygen {
    private static int getRandom(int limit) {
        return (int)(Math.random() * limit);
    }

    public static void main(String[] arg) {
        StringBuilder sb = new StringBuilder();
        int checksum = 0;

        for (int i=0; i<9; i++) {
            int n = getRandom(10);
            sb.append((char)('0' + n));
            checksum = (checksum << 2) + n;
        }

        System.out.println("- sb: " + sb);
        System.out.println("- checksum: " + checksum);
        System.out.println("- checksum % 99: " + (checksum % 99));
    }
}