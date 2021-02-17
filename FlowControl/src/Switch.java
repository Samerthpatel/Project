
public class Switch {
	public static void main(String[] args) {
		System.out.println("Java Switch Exercise 1");
		int day = 2;
		switch (day) {
		case 1:
			System.out.println("Saturday");
			break;
		case 2:
			System.out.println("Sunday");
			break;
		}
		System.out.println("Java Switch Exercise 2");
		int night = 4;
		switch (night) {
		case 1:
			System.out.println("Saturday");
			break;
		case 2:
			System.out.println("Sunday");
			break;
		default:
			System.out.println("Weekend");
		}
	}
}
