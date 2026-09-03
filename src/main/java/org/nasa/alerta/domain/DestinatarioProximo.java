package org.nasa.alerta.domain;

/**
 * Alguem que precisa ser avisado, e por que.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o resultado da pergunta central do sistema: "quem
 * está perto deste desastre e como falo com essa pessoa?". Uma linha aqui é um aviso a
 * caminho.</p>
 *
 * <p><b>POR QUE ESTE TIPO EXISTE, E NÃO SE USA {@code Cliente}, {@code Endereco} e
 * {@code Contato}.</b> A regra da arquitetura é dura e está certa: <b>fatia não conhece
 * fatia</b>. A fatia de alerta não importa nenhuma das outras — ela tem o próprio
 * <b>modelo de leitura</b>, montado com SQL próprio sobre o esquema, que é compartilhado e
 * pertence ao peer {@code persistencia}. O ganho não é burocrático: se amanhã o cadastro
 * de cliente mudar de forma, o alerta continua compilando, e a única coisa a ajustar é uma
 * consulta — em vez de uma cascata por quatro fatias.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Só chega aqui quem tem e-mail de EMERGÊNCIA.</b> A consulta filtra por tipo;
 *       um contato comum não vira destinatário, porque ninguém o inscreveu nisso.</li>
 *   <li><b>A distância vem calculada</b>, e é a real, da geodésia — não a da caixa.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não tem: é um resultado de consulta.</p>
 *
 * @param inscritoId    quem
 * @param nomeInscrito  como a pessoa é chamada no aviso
 * @param destino      o e-mail do contato de emergência
 * @param eventoId     qual evento
 * @param eventoTitulo o nome do evento, como a NASA publica
 * @param distanciaKm  distância real entre o endereço e o evento
 */
public record DestinatarioProximo(long inscritoId, String nomeInscrito, String destino,
                                  long eventoId, String eventoTitulo, double distanciaKm) {
}
