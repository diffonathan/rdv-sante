package ma.rdvsante.patients;

import org.springframework.boot.SpringApplication;

public class TestPatientsApplication {

	public static void main(String[] args) {
		SpringApplication.from(PatientsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
