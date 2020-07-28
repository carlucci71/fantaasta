package com.example.demo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.entity.Allenatori;
import com.example.demo.entity.Configurazione;
import com.example.demo.entity.Fantarose;
import com.example.demo.entity.Giocatori;
import com.example.demo.repository.AllenatoriRepository;
import com.example.demo.repository.ConfigurazioneRepository;
import com.example.demo.repository.FantaroseRepository;
import com.example.demo.repository.GiocatoriRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RestController
@RequestMapping({ "/" })
public class MyController {

	@Autowired Environment environment;	
	@Autowired HttpSession httpSession;
	@Autowired AllenatoriRepository allenatoriRepository;
	@Autowired FantaroseRepository fantaroseRepository;
	@Autowired GiocatoriRepository giocatoriRepository;
	@Autowired ConfigurazioneRepository configurazioneRepository;
	@Autowired Criptaggio criptaggio; 
	@Autowired EntityManager em;

	@RequestMapping("/init")
	public Map<String, Object> init() {
		Map<String, Object> m = new HashMap<>();
		Configurazione configurazione = getConfigurazione();
		if (configurazione.getNumeroGiocatori()==null) {
			m.put("DA_CONFIGURARE", "x");
		}
		else {
		String giocatoreLoggato = (String) httpSession.getAttribute("giocatoreLoggato");
		//		System.out.println(httpSession.getId() + "-" + giocatoreLoggato + "-" + "init");
		String idLoggato = (String) httpSession.getAttribute("idLoggato");
		if (giocatoreLoggato != null) {
			m.put("giocatoreLoggato", giocatoreLoggato);
			m.put("idLoggato", idLoggato);
		}
		m.put("elencoAllenatori", getAllAllenatori());
		}
		return m;
	}

	@PostMapping("/aggiornaNumUtenti")
	public void aggiornaNumUtenti(@RequestBody int numUtenti) throws Exception {
		Configurazione configurazione = getConfigurazione();
		configurazione.setNumeroGiocatori(numUtenti);
		configurazioneRepository.save(configurazione);
		for(int i=0;i<numUtenti;i++) {
			Allenatori al = new Allenatori();
			al.setId(i);
			al.setNome("GIOC"+i);
			if (i==0)
				al.setIsAdmin(true);
			else
				al.setIsAdmin(false);
			al.setPwd("");
			allenatoriRepository.save(al);
		}
	}
	
	@PostMapping("/aggiornaUtenti")
	public void aggiornaUtenti(@RequestBody List<Map<String, Object>> body) throws Exception {
		System.out.println(body);
		for (Map<String, Object> map : body) {
			Allenatori al = allenatoriRepository.findOne((Integer) map.get("id"));
			String nome = (String) map.get("nome");
			al.setNome(nome);
			String pwd = (String) map.get("pwd");
			al.setPwd(criptaggio.encrypt(pwd,nome));
			if("true".equalsIgnoreCase(map.get("isAdmin").toString()))
				al.setIsAdmin(true);
			else
				al.setIsAdmin(false);
			allenatoriRepository.save(al);
		}
	}

	@GetMapping("/cripta")
	public String cripta(@RequestParam(name = "pwd") String pwd,@RequestParam(name = "key") String key) throws Exception {
		return criptaggio.encrypt(pwd, key);
	}

	@GetMapping("/decripta")
	public String decripta(@RequestParam(name = "pwd") String pwd,@RequestParam(name = "key") String key) throws Exception {
		return criptaggio.decrypt(pwd, key);
	}
	
	@PostMapping("/confermaAsta")
	public void confermaAsta(@RequestBody Map<String, Object> body) throws Exception {
		//		String nomegiocatore = (String) body.get("nomegiocatore");
		//		String nomeCalciatore = (String) body.get("nomeCalciatore");
		String idgiocatore =  body.get("idgiocatore").toString();
		Integer offerta = (Integer) body.get("offerta");
		String idCalciatore = body.get("idCalciatore").toString();
		Calendar c = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
		String stm = sdf.format(c.getTime());
		Fantarose fantarosa = new Fantarose();
		fantarosa.setCosto(offerta);
		fantarosa.setIdAllenatore(Integer.parseInt(idgiocatore));
		fantarosa.setIdGiocatore(Integer.parseInt(idCalciatore));
		fantarosa.setSqlTime(stm);
		fantaroseRepository.save(fantarosa);
	}


	@RequestMapping("/spesoAllenatori")
	public List<Map<String, Object>>  spesoAllenatori() {
		try {
			String sql = "select sum(costo) costo, a.nome from fantarose f, allenatori a where a.id = idAllenatore group by a.nome";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("costo",  row[0]);
				m.put("nome",row[1]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}


	@RequestMapping("/elencoCronologiaOfferte")
	public List<Map<String, Object>>  elencoCronologiaOfferte() {
		try {
			String sql = "select a.Nome allenatore, g.Squadra, g.Ruolo, g.nome giocatore, Costo, sqlTime, idGiocatore, idAllenatore   from  fantarose f, " + 
					"giocatori g, allenatori a  where g.id = idGiocatore and a.id = idAllenatore order by sqlTime desc";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("allenatore",  row[0]);
				m.put("squadra", row[1]);
				m.put("ruolo",  row[2]);
				m.put("giocatore",  row[3]);
				m.put("costo",  row[4]);
				m.put("sqlTime",row[5]);
				m.put("idGiocatore",  row[6]);
				m.put("idAllenatore", row[7]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@RequestMapping("/elencoOfferte")
	public List<Map<String, Object>>  elencoOfferte() {
		try {
			String sql = "select a.Nome allenatore, g.Squadra, g.Ruolo, g.nome giocatore, Costo, sqlTime from fantarose f, giocatori g, " + 
					"allenatori a where g.id = idGiocatore and a.id = idAllenatore order by allenatore, ruolo desc, giocatore";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("allenatore", row[0]);
				m.put("squadra", row[1]);
				m.put("ruolo", row[2]);
				m.put("giocatore", row[3]);
				m.put("costo", row[4]);
				m.put("sqlTime", row[5]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	private ObjectMapper mapper = new ObjectMapper();
	public String toJson(Object o)
	{
		try
		{
			byte[] data = mapper.writeValueAsBytes(o);
			return new String(data);//, Charsets.ISO_8859_1
		} catch (JsonProcessingException e)
		{
			throw new RuntimeException(e);
		} 
	}
	public List<Map<String, Object>> jsonToList(String json)
	{
		try
		{
			return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
		} catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
	@RequestMapping("/riepilogoAllenatori")
	public List<Map<String, Object>>  riepilogoAllenatori() {
		try {
			String sql = "select count(ruolo) conta, ruolo, a.nome nome from fantarose f, allenatori a, giocatori g where g.id=idGiocatore " + 
					"and a.id = idAllenatore group by a.nome ,ruolo order by a.nome, ruolo desc";
			Query qy = em.createNativeQuery(sql);
			List<Object[]> resultList = qy.getResultList();
			List<Map<String, Object>> ret = new ArrayList<>();
			for (Object[] row : resultList) {
				Map<String, Object> m = new HashMap<>();
				m.put("conta",  row[0]);
				m.put("ruolo",row[1]);
				m.put("nome",row[2]);
				ret.add(m);
			}
			return ret;
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}

	@Cacheable(cacheNames = "allenatori")
	@GetMapping(path="/allAllenatori")
	public @ResponseBody Iterable<Allenatori> getAllAllenatori() {
		return allenatoriRepository.findAll();
	}	

	@GetMapping(path="/allFantarose")
	public @ResponseBody Iterable<Fantarose> getAllFantarose() {
		return fantaroseRepository.findAll();
	}	

	@GetMapping(path="/allGiocatori")
	public @ResponseBody Iterable<Giocatori> getAllGiocatori() {
		return giocatoriRepository.findAll();
	}	

	@GetMapping(path="/configurazione")
	public @ResponseBody Configurazione getConfigurazione() {
		return configurazioneRepository.findOne(1);
	}	
	
	@GetMapping(path="/giocatoriLiberi")
	public @ResponseBody List<Map<String, Object>> getGiocatoriLiberi() {
//		Iterable<Giocatori> giocatoriLiberi = giocatoriRepository.getGiocatoriLiberi();
		List<Object[]> resultList = giocatoriRepository.getGiocatoriLiberi();
		List<Map<String, Object>> ret = new ArrayList<>();
//		for (Giocatori giocatori : giocatoriLiberi) {
//			System.out.println(giocatori);
//		}
		for (Object[] row : resultList) {
			Map<String, Object> m = new HashMap<>();
			m.put("id",  row[0]);
			m.put("squadra",  row[1]);
			m.put("nome",  row[2]);
			m.put("ruolo",  row[3]);
			m.put("quotazione",  row[4]);
			ret.add(m);
		}
		return ret;
	}	

}
