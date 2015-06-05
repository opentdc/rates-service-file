/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 Arbalo AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.opentdc.rates.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import javax.servlet.ServletContext;

import org.opentdc.file.AbstractFileServiceProvider;
import org.opentdc.rates.RatesModel;
import org.opentdc.rates.ServiceProvider;
import org.opentdc.service.exception.DuplicateException;
import org.opentdc.service.exception.InternalServerErrorException;
import org.opentdc.service.exception.NotAllowedException;
import org.opentdc.service.exception.NotFoundException;
import org.opentdc.service.exception.ValidationException;
import org.opentdc.util.PrettyPrinter;

public class FileServiceProvider extends AbstractFileServiceProvider<RatesModel> implements ServiceProvider {
	
	private static Map<String, RatesModel> index = null;
	private static final Logger logger = Logger.getLogger(FileServiceProvider.class.getName());

	public FileServiceProvider(
		ServletContext context, 
		String prefix
	) throws IOException {
		super(context, prefix);
		if (index == null) {
			index = new HashMap<String, RatesModel>();
			List<RatesModel> _rates = importJson();
			for (RatesModel _rate : _rates) {
				index.put(_rate.getId(), _rate);
			}
			logger.info(_rates.size() + " Rates imported.");
		}
	}

	@Override
	public ArrayList<RatesModel> list(
		String query,
		String queryType,
		long position,
		long size
	) {
		ArrayList<RatesModel> _rates = new ArrayList<RatesModel>(index.values());
		Collections.sort(_rates, RatesModel.RateComparator);
		ArrayList<RatesModel> _selection = new ArrayList<RatesModel>();
		for (int i = 0; i < _rates.size(); i++) {
			if (i >= position && i < (position + size)) {
				_selection.add(_rates.get(i));
			}			
		}
		logger.info("list(<" + query + ">, <" + queryType + 
				">, <" + position + ">, <" + size + ">) -> " + _selection.size() + " rates.");
		return _selection;
	}

	@Override
	public RatesModel create(
			RatesModel rate) 
		throws DuplicateException, ValidationException {
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(rate) + ")");
		String _id = rate.getId();
		if (_id == null || _id == "") {
			_id = UUID.randomUUID().toString();
		} else {
			if (index.get(_id) != null) {
				// object with same ID exists already
				throw new DuplicateException("rate <" + _id + "> exists already.");
			}
			else { 	// a new ID was set on the client; we do not allow this
				throw new ValidationException("rate <" + _id + 
					"> contains an ID generated on the client. This is not allowed.");
			}
		}
		rate.setId(_id);
		Date _date = new Date();
		rate.setCreatedAt(_date);
		rate.setCreatedBy("TEST_USER");
		rate.setModifiedAt(_date);
		rate.setModifiedBy("TEST_USER");
		index.put(_id, rate);
		logger.info("create(" + PrettyPrinter.prettyPrintAsJSON(rate) + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
		return rate;
	}

	@Override
	public RatesModel read(
			String id) 
		throws NotFoundException {
		RatesModel _rate = index.get(id);
		if (_rate == null) {
			throw new NotFoundException("no rate with ID <" + id
					+ "> was found.");
		}
		logger.info("read(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_rate));
		return _rate;
	}

	@Override
	public RatesModel update(
		String id, 
		RatesModel rate
	) throws NotFoundException, NotAllowedException {
		RatesModel _rate = index.get(id);
		if(_rate == null) {
			throw new NotFoundException("no rate with ID <" + id
					+ "> was found.");
		} 
		if (! _rate.getCreatedAt().equals(rate.getCreatedAt())) {
			throw new NotAllowedException("rate <" + id + ">: it is not allowed to change createdAt on the client.");
		}
		if (! _rate.getCreatedBy().equalsIgnoreCase(rate.getCreatedBy())) {
			throw new NotAllowedException("rate <" + id + ">: it is not allowed to change createdBy on the client.");
		}
		_rate.setTitle(rate.getTitle());
		_rate.setRate(rate.getRate());
		_rate.setCurrency(rate.getCurrency());
		_rate.setDescription(rate.getDescription());
		_rate.setModifiedAt(new Date());
		_rate.setModifiedBy("DUMMY_USER");
		index.put(rate.getId(), _rate);
		logger.info("update(" + id + ") -> " + PrettyPrinter.prettyPrintAsJSON(_rate));
		if (isPersistent) {
			exportJson(index.values());
		}
		return rate;
	}

	@Override
	public void delete(
			String id) 
		throws NotFoundException, InternalServerErrorException {
		RatesModel _rate = index.get(id);
		if (_rate == null) {
			throw new NotFoundException("rate <" + id
					+ "> was not found.");
		}
		if (index.remove(id) == null) {
			throw new InternalServerErrorException("rate <" + id
					+ "> can not be removed, because it does not exist in the index");
		}
		logger.info("delete(" + id + ")");
		if (isPersistent) {
			exportJson(index.values());
		}
	}
}
