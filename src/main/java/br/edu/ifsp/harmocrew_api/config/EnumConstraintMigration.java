package br.edu.ifsp.harmocrew_api.config;

import java.util.List;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/*
 * Corrige bancos PostgreSQL criados por versoes antigas da API.
 *
 * O Hibernate cria CHECK constraints para colunas enum (ex.: status IN
 * ('PENDING','APPROVED',...)). Com ddl-auto=update ele adiciona tabelas e
 * colunas novas, mas NUNCA atualiza essas constraints. Quando um enum ganha
 * valores (UNDER_REVIEW, SHORTLISTED...), gravar o novo valor falha com
 * "violates check constraint" (HTTP 500).
 *
 * Na inicializacao removemos as CHECK constraints de enum ("<coluna> IN (...)")
 * das nossas tabelas. A validacao continua garantida pelo tipo enum do Java.
 */
@Component
public class EnumConstraintMigration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(EnumConstraintMigration.class);

	private final DataSource dataSource;

	public EnumConstraintMigration(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void run(ApplicationArguments args) {
		try (var connection = dataSource.getConnection()) {
			if (!"PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName())) {
				return;
			}
		}
		catch (Exception exception) {
			return;
		}
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		List<String[]> constraints = jdbc.query("""
			SELECT rel.relname, con.conname
			  FROM pg_constraint con
			  JOIN pg_class rel ON rel.oid = con.conrelid
			  JOIN pg_namespace ns ON ns.oid = rel.relnamespace
			 WHERE con.contype = 'c'
			   AND ns.nspname = current_schema()
			   AND pg_get_constraintdef(con.oid) LIKE '%ARRAY[%'
			""", (rs, i) -> new String[] {rs.getString(1), rs.getString(2)});
		for (String[] constraint : constraints) {
			jdbc.execute("ALTER TABLE \"" + constraint[0] + "\" DROP CONSTRAINT IF EXISTS \"" + constraint[1] + "\"");
			log.info("Removida CHECK constraint de enum desatualizavel: {}.{}", constraint[0], constraint[1]);
		}
	}
}
