package io.github.candyeer.k8s;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author lican
 */
@Controller
@RequestMapping("xterm")
public class XTermController {

    @RequestMapping
    public String xterm(Model model, String namespace, String podName) {
        model.addAttribute("namespace", namespace);
        model.addAttribute("podName", podName);
        return "xterm";
    }
}