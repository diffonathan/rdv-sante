package ma.rdvsante.rendezvous;

import org.springframework.boot.SpringApplication;

public class TestRendezvousApplication {

	public static void main(String[] args) {
		SpringApplication.from(RendezvousApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
