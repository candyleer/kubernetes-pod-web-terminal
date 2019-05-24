package io.github.candyeer.k8s;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * @author lican
 */
@Controller
@RequestMapping
public class XTermController {

    @GetMapping
    public String xterm(Model model) {
        return "xterm";
    }
}